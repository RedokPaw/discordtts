package org.redok.tts;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Неофициальный эндпоинт озвучки Google Translate: отдаёт MP3, принимает не больше 200 символов за запрос. */
public class GoogleTranslateTts implements TtsEngine {

    private static final int MAX_TEXT_LENGTH = 200; // на 201 символ Google отвечает 400
    private static final String DEFAULT_ENDPOINT = "https://translate.google.com/translate_tts";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final String endpoint;
    private final String language;
    private final Duration timeout;

    public GoogleTranslateTts(String language) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                DEFAULT_ENDPOINT, language, DEFAULT_TIMEOUT);
    }

    /** Адрес и таймаут меняются только в тестах: там вместо Google — локальный HTTP-сервер. */
    GoogleTranslateTts(HttpClient httpClient, String endpoint, String language, Duration timeout) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.language = language;
        this.timeout = timeout;
    }

    @Override
    public int maxTextLength() {
        return MAX_TEXT_LENGTH;
    }

    @Override
    public byte[] synthesize(String text) throws IOException, InterruptedException {
        URI uri = URI.create(endpoint + "?ie=UTF-8&total=1&idx=0&client=tw-ob"
                + "&tl=" + URLEncoder.encode(language, StandardCharsets.UTF_8)
                + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        CompletableFuture<HttpResponse<byte[]>> call =
                httpClient.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<byte[]> response;
        try {
            // Ждём весь ответ вместе с телом (MP3 — до ~160 КиБ), но не дольше timeout.
            response = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new HttpTimeoutException("Google TTS did not answer in " + timeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        }
        if (response.statusCode() != 200) {
            throw new IOException("Google TTS answered HTTP " + response.statusCode());
        }
        return response.body();
    }
}
