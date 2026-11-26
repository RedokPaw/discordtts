package org.redok.tts;

import org.redok.audio.AudioDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/** Текст → PCM: режет на куски, синтезирует и декодирует в пуле потоков, кладёт результат в слоты очереди. */
public class TtsSynthesizer {

    private static final Logger log = LoggerFactory.getLogger(TtsSynthesizer.class);
    private final TtsEngine engine;
    private final AudioDecoder decoder;
    private final Executor executor; // ограниченный пул: его размер и есть лимит одновременных синтезов
    private final int maxChunksPerMessage;

    public TtsSynthesizer(TtsEngine engine, AudioDecoder decoder, Executor executor, int maxChunksPerMessage) {
        this.engine = engine;
        this.decoder = decoder;
        this.executor = executor;
        this.maxChunksPerMessage = maxChunksPerMessage;
    }

    /** Режет текст на куски, которые движок примет за один запрос; не больше maxChunksPerMessage кусков. */
    public List<String> split(String text) {
        List<String> chunks = splitByWords(text, engine.maxTextLength());
        if (log.isDebugEnabled()) {
            log.debug("text of {} chars split into {} chunks of {} chars (limit {})", text.length(), chunks.size(),
                    chunks.stream().map(String::length).toList(), engine.maxTextLength());
        }
        if (chunks.size() <= maxChunksPerMessage) {
            return chunks;
        }
        List<String> dropped = chunks.subList(maxChunksPerMessage, chunks.size());
        log.debug("only the first {} chunks are kept (TTS_MAX_CHUNKS), {} chunks / {} chars dropped, starting with «{}»",
                maxChunksPerMessage, dropped.size(), dropped.stream().mapToInt(String::length).sum(),
                preview(dropped.getFirst()));
        return List.copyOf(chunks.subList(0, maxChunksPerMessage));
    }

    /**
     * Синтезирует кусок в пуле и кладёт PCM в слот; ошибку синтеза — тоже в слот.
     *
     * @param label подпись для логов: гильдия, id слота и номер куска
     */
    public void synthesizeInto(CompletableFuture<ByteBuffer> slot, String text, String label) {
        long submitted = System.nanoTime();
        executor.execute(() -> {
            long started = System.nanoTime();
            if (slot.isDone()) {
                // бот уже вышел (слот отменён) или слот просрочен — не тратим ресурсы
                log.debug("[{}] skipped: slot is already {} after {} ms in the pool queue", label,
                        slot.isCancelled() ? "cancelled" : "completed (timed out)", millisSince(submitted));
                return;
            }
            log.debug("[{}] synthesizing {} chars after {} ms in the pool queue: «{}»",
                    label, text.length(), millisSince(submitted), preview(text));
            try {
                byte[] encoded = engine.synthesize(text);
                long synthesized = System.nanoTime();
                byte[] pcm = decoder.toPcm(encoded);
                boolean accepted = slot.complete(ByteBuffer.wrap(pcm));
                log.debug("[{}] done: TTS {} bytes in {} ms, decoded {} bytes PCM in {} ms{}", label,
                        encoded.length, TimeUnit.NANOSECONDS.toMillis(synthesized - started),
                        pcm.length, millisSince(synthesized),
                        accepted ? "" : " — but the slot was already completed (timed out or cancelled), result dropped");
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("[{}] TTS failed after {} ms", label, millisSince(started), e);
                slot.completeExceptionally(e);
            }
        });
    }

    /** Режет текст по словам на куски не длиннее maxLength; слово длиннее лимита режется жёстко. */
    static List<String> splitByWords(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            while (word.length() > maxLength) {
                addChunk(chunks, chunk);
                chunks.add(word.substring(0, maxLength));
                word = word.substring(maxLength);
            }
            if (!chunk.isEmpty() && chunk.length() + 1 + word.length() > maxLength) {
                addChunk(chunks, chunk);
            }
            if (!chunk.isEmpty()) {
                chunk.append(' ');
            }
            chunk.append(word);
        }
        addChunk(chunks, chunk);
        return chunks;
    }

    private static void addChunk(List<String> chunks, StringBuilder chunk) {
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
            chunk.setLength(0);
        }
    }

    private static long millisSince(long nanoTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoTime);
    }

    private static String preview(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "…";
    }
}
