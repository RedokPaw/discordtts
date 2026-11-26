package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class AudioSendHandlerImpl implements AudioSendHandler {

    private static final int FRAME_SIZE = 3840; // 20 мс PCM: 48000 Гц × 2 канала × 2 байта × 0.02 с
    private static final long SLOT_TIMEOUT_SECONDS = 30;

    // Слоты в порядке прихода сообщений. Слот — обещание PCM-буфера, который, возможно, ещё синтезируется.
    private final BlockingQueue<CompletableFuture<ByteBuffer>> ttsQueue;
    private final ByteBuffer frame = ByteBuffer.allocate(FRAME_SIZE);
    private ByteBuffer ttsBuffer; // играющий сейчас буфер; трогает только аудиопоток JDA

    public AudioSendHandlerImpl(int capacity) {
        this.ttsQueue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * Вызывается из потока событий JDA в момент прихода сообщения. Резервирует места под все его куски:
     * либо все сразу, либо ни одного, чтобы сообщение не оборвалось на середине из-за заполненной очереди.
     *
     * @return пустые слоты в порядке воспроизведения или пустой список, если мест не хватает
     */
    public synchronized List<CompletableFuture<ByteBuffer>> reserveSlots(int count) {
        if (ttsQueue.remainingCapacity() < count) {
            return List.of();
        }
        List<CompletableFuture<ByteBuffer>> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Таймаут гарантирует, что слот разрешится: зависший синтез не заблокирует очередь навсегда.
            CompletableFuture<ByteBuffer> slot = new CompletableFuture<ByteBuffer>()
                    .orTimeout(SLOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ttsQueue.add(slot); // место есть: добавляем только здесь (synchronized), аудиопоток лишь освобождает
            slots.add(slot);
        }
        return slots;
    }

    /** При выходе из канала: отменяем все ожидающие слоты, их буферы сразу становятся мусором. */
    public void clear() {
        CompletableFuture<ByteBuffer> slot;
        while ((slot = ttsQueue.poll()) != null) {
            slot.cancel(false);
        }
    }

    @Override
    public boolean canProvide() {
        while (ttsBuffer == null || !ttsBuffer.hasRemaining()) {
            ttsBuffer = null; // доигранный буфер отпускаем сразу, не дожидаясь следующего сообщения
            CompletableFuture<ByteBuffer> head = ttsQueue.peek();
            if (head == null || !head.isDone()) {
                return false; // голова ещё готовится — ждём, даже если следующие слоты уже готовы
            }
            ttsQueue.poll();
            // Ошибка, таймаут или отмена — пропускаем слот и сразу смотрим следующий.
            ttsBuffer = head.isCompletedExceptionally() ? null : head.join();
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
}
