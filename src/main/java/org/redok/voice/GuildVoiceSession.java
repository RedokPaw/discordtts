package org.redok.voice;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Очередь речи одной гильдии и источник звука для JDA. Слоты резервируются в порядке прихода
 * сообщений и заполняются синтезом позже; аудиопоток играет только голову очереди.
 */
public class GuildVoiceSession implements AudioSendHandler {

    private static final Logger log = LoggerFactory.getLogger(GuildVoiceSession.class);
    private static final int FRAME_SIZE = 3840; // 20 мс PCM: 48000 Гц × 2 канала × 2 байта × 0.02 с
    private static final int PCM_BYTES_PER_SECOND = 48000 * 2 * 2;
    private static final Duration DEFAULT_SLOT_TIMEOUT = Duration.ofSeconds(30);

    private final String guildId;
    // Слоты в порядке прихода сообщений. Слот — обещание PCM-буфера, который, возможно, ещё синтезируется.
    private final BlockingQueue<CompletableFuture<ByteBuffer>> ttsQueue;
    private final Duration slotTimeout;
    private final ByteBuffer frame = ByteBuffer.allocate(FRAME_SIZE);
    // Дальше — состояние аудиопотока JDA, другие потоки его не трогают.
    private ByteBuffer ttsBuffer;                       // играющий сейчас буфер
    private String playingSlot;                         // id играющего слота, для лога
    private CompletableFuture<ByteBuffer> waitingFor;   // голова, которую уже ждём, чтобы писать в лог один раз
    private long waitingSince;

    public GuildVoiceSession(String guildId, int capacity) {
        this(guildId, capacity, DEFAULT_SLOT_TIMEOUT);
    }

    /** Таймаут слота меняется только в тестах. */
    GuildVoiceSession(String guildId, int capacity, Duration slotTimeout) {
        this.guildId = guildId;
        this.ttsQueue = new LinkedBlockingQueue<>(capacity);
        this.slotTimeout = slotTimeout;
    }

    /** Короткий id слота для логов: по нему кусок видно от нарезки до воспроизведения. */
    static String slotId(CompletableFuture<?> slot) {
        return Integer.toHexString(System.identityHashCode(slot));
    }

    /**
     * Вызывается из потока событий JDA в момент прихода сообщения. Резервирует места под все его куски:
     * либо все сразу, либо ни одного, чтобы сообщение не оборвалось на середине из-за заполненной очереди.
     *
     * @return пустые слоты в порядке воспроизведения или пустой список, если мест не хватает
     */
    public synchronized List<CompletableFuture<ByteBuffer>> reserveSlots(int count) {
        int free = ttsQueue.remainingCapacity();
        if (free < count) {
            log.debug("[{}] no room for {} chunks: {} of {} places free", guildId, count, free, free + ttsQueue.size());
            return List.of();
        }
        List<CompletableFuture<ByteBuffer>> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Таймаут гарантирует, что слот разрешится: зависший синтез не заблокирует очередь навсегда.
            CompletableFuture<ByteBuffer> slot = new CompletableFuture<ByteBuffer>()
                    .orTimeout(slotTimeout.toMillis(), TimeUnit.MILLISECONDS);
            ttsQueue.add(slot); // место есть: добавляем только здесь (synchronized), аудиопоток лишь освобождает
            slots.add(slot);
        }
        if (log.isDebugEnabled()) {
            log.debug("[{}] reserved {} slots {}, {} places left", guildId, count,
                    slots.stream().map(GuildVoiceSession::slotId).toList(), ttsQueue.remainingCapacity());
        }
        return slots;
    }

    /** Отменяет все ожидающие слоты, их буферы сразу становятся мусором. */
    public void clear() {
        int cancelled = 0;
        CompletableFuture<ByteBuffer> slot;
        while ((slot = ttsQueue.poll()) != null) {
            slot.cancel(false);
            cancelled++;
        }
        log.debug("[{}] queue cleared, {} pending slots cancelled", guildId, cancelled);
    }

    @Override
    public boolean canProvide() {
        while (ttsBuffer == null || !ttsBuffer.hasRemaining()) {
            if (ttsBuffer != null) {
                log.debug("[{}] slot {} finished playing", guildId, playingSlot);
            }
            ttsBuffer = null; // доигранный буфер отпускаем сразу, не дожидаясь следующего сообщения
            CompletableFuture<ByteBuffer> head = ttsQueue.peek();
            if (head == null) {
                return false;
            }
            if (!head.isDone()) {
                if (head != waitingFor) { // пишем один раз на слот, а не каждые 20 мс
                    waitingFor = head;
                    waitingSince = System.nanoTime();
                    log.debug("[{}] head slot {} is not synthesized yet, waiting ({} slots in queue)",
                            guildId, slotId(head), ttsQueue.size());
                }
                return false; // голова ещё готовится — ждём, даже если следующие слоты уже готовы
            }
            ttsQueue.poll();
            long waitedMs = head == waitingFor ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitingSince) : 0;
            waitingFor = null;
            if (head.isCompletedExceptionally()) {
                // Ошибка, таймаут или отмена — пропускаем слот и сразу смотрим следующий.
                log.debug("[{}] slot {} skipped after waiting {} ms: {}", guildId, slotId(head), waitedMs, failureOf(head));
                continue;
            }
            ttsBuffer = head.join();
            playingSlot = slotId(head);
            log.debug("[{}] slot {} starts playing: {} bytes PCM (~{} s), waited {} ms, {} slots left in queue",
                    guildId, playingSlot, ttsBuffer.remaining(),
                    String.format("%.1f", ttsBuffer.remaining() / (double) PCM_BYTES_PER_SECOND), waitedMs, ttsQueue.size());
        }
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        int n = Math.min(ttsBuffer.remaining(), FRAME_SIZE);
        frame.clear();
        frame.put(0, ttsBuffer, ttsBuffer.position(), n); // абсолютный put (Java 16+), позиции не двигает
        ttsBuffer.position(ttsBuffer.position() + n);
        Arrays.fill(frame.array(), n, FRAME_SIZE, (byte) 0); // хвост последнего кадра — тишина
        return frame;
    }

    @Override
    public boolean isOpus() {
        return false;
    }

    /** Почему слот завершился ошибкой: таймаут, отмена или исключение синтеза. */
    private static String failureOf(CompletableFuture<ByteBuffer> slot) {
        try {
            slot.join();
            return "no failure";
        } catch (CancellationException e) {
            return "cancelled";
        } catch (CompletionException e) {
            return e.getCause() instanceof TimeoutException ? "slot timed out" : String.valueOf(e.getCause());
        }
    }
}
