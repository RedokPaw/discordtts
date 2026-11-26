package org.redok.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

//TODO: Отделить гуглттс, сделать аудиопровайдера кастомайзбл, конвертирование тоже увести в другой класс
/** Текст → PCM: режет на куски, синтезирует и декодирует в пуле потоков, кладёт результат в слоты очереди. */
public class TtsSynthesizer {

    private static final int MAX_TTS_LENGTH = 200;       // больше Google TTS не принимает: на 201 символ отвечает 400
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(10);
    private static final String TTS_URL =
            "https://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&client=tw-ob&tl=%s&q=%s";

    private static final Logger log = LoggerFactory.getLogger(TtsSynthesizer.class);
    private final String language;
    private final int maxChunksPerMessage; // сколько кусков по MAX_TTS_LENGTH читать из одного сообщения
    // Пул сам ограничивает параллельность: не больше threads синтезов (HTTP + ffmpeg) на весь бот.
    private final ExecutorService synthPool;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg",
            "-loglevel", "error",
            "-threads", "1",
            "-i", "pipe:0",
            "-f", "s16be",
            "-ar", "48000",
            "-ac", "2",
            "pipe:1"
    ).redirectError(ProcessBuilder.Redirect.DISCARD); // stderr никто не читает — не даём ему забить пайп

    public TtsSynthesizer(String language, int threads, int maxChunksPerMessage) {
        this.language = language;
        this.maxChunksPerMessage = maxChunksPerMessage;
        this.synthPool = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("tts-", 1).factory());
    }

    /** Режет текст на куски, которые Google TTS примет за один запрос; не больше maxChunksPerMessage кусков. */
    public List<String> split(String text) {
        return splitForTts(text, maxChunksPerMessage);
    }

    /** Синтезирует кусок в пуле и кладёт PCM в слот; ошибку синтеза — тоже в слот. */
    public void synthesizeInto(CompletableFuture<ByteBuffer> slot, String text, String guildId) {
        synthPool.execute(() -> {
            if (slot.isDone()) {
                return; // бот уже вышел (слот отменён) или слот просрочен — не тратим ресурсы
            }
            try {
                slot.complete(convertAudioToPCM(getAudioFromGoogleTTS(text)));
            } catch (Exception e) {
                log.warn("TTS failed in guild {}", guildId, e);
                slot.completeExceptionally(e);
            }
        });
    }

    /**
     * Режет текст по словам на куски не длиннее MAX_TTS_LENGTH; слово длиннее лимита режется жёстко.
     * Возвращает не больше maxChunks кусков. Пакетный доступ — для проверки без Discord.
     */
    static List<String> splitForTts(String text, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            while (word.length() > MAX_TTS_LENGTH) {
                addChunk(chunks, chunk);
                chunks.add(word.substring(0, MAX_TTS_LENGTH));
                word = word.substring(MAX_TTS_LENGTH);
            }
            if (!chunk.isEmpty() && chunk.length() + 1 + word.length() > MAX_TTS_LENGTH) {
                addChunk(chunks, chunk);
            }
            if (!chunk.isEmpty()) {
                chunk.append(' ');
            }
            chunk.append(word);
        }
        addChunk(chunks, chunk);
        return chunks.size() > maxChunks ? List.copyOf(chunks.subList(0, maxChunks)) : chunks;
    }

    private static void addChunk(List<String> chunks, StringBuilder chunk) {
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
            chunk.setLength(0);
        }
    }

    private byte[] getAudioFromGoogleTTS(String text) throws Exception {
        String url = TTS_URL.formatted(language, URLEncoder.encode(text, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
        CompletableFuture<HttpResponse<byte[]>> call =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            // Ждём весь ответ вместе с телом (MP3 — до ~160 КиБ), но не дольше HTTP_TIMEOUT.
            HttpResponse<byte[]> response = call.get(HTTP_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (response.statusCode() != 200) {
                throw new IOException("Google TTS answered HTTP " + response.statusCode());
            }
            return response.body();
        } catch (TimeoutException e) {
            call.cancel(true);
            throw e;
        }
    }

    private ByteBuffer convertAudioToPCM(byte[] mp3) throws IOException, InterruptedException {
        return ByteBuffer.wrap(runProcess(pb, mp3, FFMPEG_TIMEOUT));
    }

    /** Прогоняет input через процесс не дольше timeout. Пакетный доступ — чтобы проверить с {@code cat} вместо ffmpeg. */
    static byte[] runProcess(ProcessBuilder pb, byte[] input, Duration timeout) throws IOException, InterruptedException {
        Process process = pb.start();
        try {
            // Сторож: если процесс завис, через timeout он будет убит, а readAllBytes ниже получит EOF.
            CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .execute(process::destroyForcibly);
            // stdin пишем параллельно с чтением stdout, иначе обе стороны упрутся в заполненный пайп.
            Thread.ofVirtual().start(() -> {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(input);
                } catch (IOException ignored) {
                    // процесс закрыл stdin раньше времени — причину покажет код выхода
                }
            });
            byte[] output;
            try (InputStream stdout = process.getInputStream()) {
                output = stdout.readAllBytes();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("process exited with code " + exitCode);
            }
            return output;
        } finally {
            process.destroyForcibly(); // если процесс уже завершился, ничего не делает
        }
    }
}
