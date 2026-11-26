package org.redok.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Silero TTS в отдельном контейнере (см. silero/ в корне репозитория): отдаёт WAV, 48 кГц.
 * Доступность проверяется в фоне запросом GET /health; сетевая ошибка синтеза сразу помечает сервис
 * недоступным, не дожидаясь проверки. Пока сервис недоступен, его голоса заменяются голосом по умолчанию.
 */
public class SileroTts implements TtsEngine {

    public static final String ID = "silero";
    // Модель v5_6_ru падает примерно на 1100 символах, а нормализация удлиняет текст: «1000» → «одна тысяча».
    static final int MAX_TEXT_LENGTH = 500;
    private static final List<VoiceInfo> VOICES = List.of(
            voice("xenia", "Ксения, женский"),
            voice("kseniya", "Ксения 2, женский"),
            voice("baya", "Бая, женский"),
            voice("aidar", "Айдар, мужской"),
            voice("eugene", "Евгений, мужской"));
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(15);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);
    private static final Logger log = LoggerFactory.getLogger(SileroTts.class);

    private final HttpClient httpClient;
    private final URI ttsUri;
    private final URI healthUri;
    private final Duration timeout;
    private final Duration checkInterval;
    private volatile boolean available; // до первой удачной проверки сервис считается недоступным

    public SileroTts(URI baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), baseUrl, DEFAULT_TIMEOUT,
                DEFAULT_CHECK_INTERVAL);
    }

    /** Клиент, таймаут и интервал проверки меняются только в тестах. */
    SileroTts(HttpClient httpClient, URI baseUrl, Duration timeout, Duration checkInterval) {
        String base = baseUrl.toString().replaceAll("/+$", "");
        this.httpClient = httpClient;
        this.ttsUri = URI.create(base + "/tts");
        this.healthUri = URI.create(base + "/health");
        this.timeout = timeout;
        this.checkInterval = checkInterval;
    }

    /** Запускает фоновую проверку /health: первая — сразу, дальше раз в checkInterval. */
    public SileroTts start() {
        ScheduledExecutorService checker = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("silero-health").daemon().factory());
        checker.scheduleWithFixedDelay(this::checkHealth, 0, checkInterval.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Silero";
    }

    @Override
    public List<VoiceInfo> voices() {
        return VOICES;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int maxTextLength() {
        return MAX_TEXT_LENGTH;
    }

    @Override
    public byte[] synthesize(String text, String voiceId) throws IOException, InterruptedException {
        String body = "{\"text\":" + jsonString(text) + ",\"speaker\":" + jsonString(voiceId) + "}";
        HttpRequest request = HttpRequest.newBuilder(ttsUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = send(request, timeout);
        } catch (IOException e) {
            setAvailable(false, e.toString()); // сервис не отвечает — не ждём следующей проверки
            throw e;
        }
        if (response.statusCode() != 200) {
            // 400 — этот текст не озвучить (например, одни эмодзи), сам сервис при этом жив
            String reason = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("Silero answered HTTP " + response.statusCode() + ": "
                    + (reason.length() > 200 ? reason.substring(0, 200) : reason));
        }
        return response.body();
    }

    /** Одна проверка /health; ошибки не выпускает, иначе планировщик перестанет её запускать. */
    void checkHealth() {
        try {
            HttpResponse<byte[]> response = send(HttpRequest.newBuilder(healthUri).build(), HEALTH_TIMEOUT);
            setAvailable(response.statusCode() == 200, "health check answered HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            setAvailable(false, "health check failed: " + e);
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, Duration limit) throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<byte[]>> call =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            // ждём весь ответ вместе с телом, но не дольше limit
            return call.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new HttpTimeoutException("Silero did not answer " + request.uri() + " in " + limit.toMillis() + " ms");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        }
    }

    /** В лог — только смена состояния, а не каждая проверка. */
    private void setAvailable(boolean now, String reason) {
        boolean before = available;
        available = now;
        if (before != now) {
            if (now) {
                log.info("Silero is available at {}", healthUri);
            } else {
                log.warn("Silero is not available, its voices fall back to the default voice: {}", reason);
            }
        }
    }

    private static VoiceInfo voice(String id, String name) {
        return new VoiceInfo(new Voice(ID, id), name);
    }

    /** JSON-строка в кавычках: экранируются кавычки, обратный слэш и управляющие символы. */
    static String jsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
