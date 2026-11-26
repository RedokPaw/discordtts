package org.redok.tts;

import org.redok.audio.AudioDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
        return chunks.size() > maxChunksPerMessage ? List.copyOf(chunks.subList(0, maxChunksPerMessage)) : chunks;
    }

    /** Синтезирует кусок в пуле и кладёт PCM в слот; ошибку синтеза — тоже в слот. */
    public void synthesizeInto(CompletableFuture<ByteBuffer> slot, String text, String guildId) {
        executor.execute(() -> {
            if (slot.isDone()) {
                return; // бот уже вышел (слот отменён) или слот просрочен — не тратим ресурсы
            }
            try {
                slot.complete(ByteBuffer.wrap(decoder.toPcm(engine.synthesize(text))));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("TTS failed in guild {}", guildId, e);
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
}
