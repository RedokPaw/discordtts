package org.redok.tts;

import net.dv8tion.jda.api.audio.AudioSendHandler;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

//TODO: Отделить гуглттс, сделать аудиопровайдера кастомайзбл, конвертирование тоже увести в другой класс
//и еще вынести кол-во threads в env
public class TTSMessageHandlerImpl implements TTSMessageHandler {

    private static final int MAX_PROCESS_THREADS = 5;
    private static final int MAX_TTS_LENGTH = 200;       // больше Google TTS не принимает: на 201 символ отвечает 400
    private static final int MAX_CHUNKS_PER_MESSAGE = 3; // ~600 символов, около минуты речи; остальное отбрасывается
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(10);
    private static final String TTS_URL =
            "https://translate.google.com/translate_tts?ie=UTF-8&total=1&idx=0&client=tw-ob&tl=%s&q=%s";

    private final Logger log = LoggerFactory.getLogger(TTSMessageHandlerImpl.class);
    private final String language = "ru";
    private final Map<String, AudioSendHandlerImpl> handlerPerGuild = new HashMap<>();
    // Пул сам ограничивает параллельность: не больше MAX_PROCESS_THREADS синтезов (HTTP + ffmpeg) на весь бот.
    private final ExecutorService synthPool =
            Executors.newFixedThreadPool(MAX_PROCESS_THREADS, Thread.ofPlatform().name("tts-", 1).factory());
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

    @Override
    public boolean handleMessageAndSpeak(String text, String guildId) {
        AudioSendHandlerImpl handler = handlerPerGuild.get(guildId);
        if (handler == null) {
            return false;
        }
        List<String> chunks = splitForTts(text);
        if (chunks.isEmpty()) {
            return true; // произносить нечего — это не переполнение очереди
        }
        // Места занимаются сразу, в порядке прихода сообщений: проверка ёмкости и вставка — одно действие.
        List<CompletableFuture<ByteBuffer>> slots = handler.reserveSlots(chunks.size());
        if (slots.isEmpty()) {
            return false;
        }
        for (int i = 0; i < chunks.size(); i++) {
            CompletableFuture<ByteBuffer> slot = slots.get(i);
            String chunk = chunks.get(i);
            synthPool.execute(() -> synthesizeInto(slot, chunk, guildId));
        }
        return true;
    }

    @Override
    public AudioSendHandler registerGuild(String guildId) {
        AudioSendHandlerImpl audioSendHandler = new AudioSendHandlerImpl();
        AudioSendHandlerImpl previous = handlerPerGuild.put(guildId, audioSendHandler);
        if (previous != null) {
            previous.clear(); // повторный /join: старая очередь больше никому не нужна
        }
        return audioSendHandler;
    }

    @Override
    public void unregisterGuild(String guildId) {
        AudioSendHandlerImpl handler = handlerPerGuild.remove(guildId);
        if (handler != null) {
            handler.clear();
        }
    }

    /**
     * Режет текст по словам на куски не длиннее MAX_TTS_LENGTH; слово длиннее лимита режется жёстко.
     * Возвращает не больше MAX_CHUNKS_PER_MESSAGE кусков. Пакетный доступ — для проверки без Discord.
     */
    static List<String> splitForTts(String text) {
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
        return chunks.size() > MAX_CHUNKS_PER_MESSAGE ? List.copyOf(chunks.subList(0, MAX_CHUNKS_PER_MESSAGE)) : chunks;
    }

    private static void addChunk(List<String> chunks, StringBuilder chunk) {
        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
            chunk.setLength(0);
        }
    }

    private void synthesizeInto(CompletableFuture<ByteBuffer> slot, String text, String guildId) {
        if (slot.isDone()) {
            return; // бот уже вышел (слот отменён) или слот просрочен — не тратим ресурсы
        }
        try {
            slot.complete(convertAudioToPCM(getAudioFromGoogleTTS(text)));
        } catch (Exception e) {
            log.warn("TTS failed in guild {}", guildId, e);
            slot.completeExceptionally(e);
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
