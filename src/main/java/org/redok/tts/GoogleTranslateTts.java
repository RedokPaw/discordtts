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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Неофициальный эндпоинт озвучки Google Translate: отдаёт MP3, принимает не больше 200 символов за запрос.
 * Разных голосов для одного языка у него нет, поэтому голос здесь — это язык: {@code google:ru}, {@code google:en}.
 */
public class GoogleTranslateTts implements TtsEngine {

    public static final String ID = "google";
    /** Языки для /voice. Все 100+ языков Google не показываем: в автодополнении это мусор. */
    public static final List<String> DEFAULT_LANGUAGES =
            List.of("ru", "en", "uk", "de", "fr", "es", "it", "pl", "tr", "ja");
    private static final Locale NAMES_LOCALE = Locale.of("ru");
    private static final int MAX_TEXT_LENGTH = 200; // на 201 символ Google отвечает 400
    private static final String DEFAULT_ENDPOINT = "https://translate.google.com/translate_tts";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final String endpoint;
    private final List<VoiceInfo> voices;
    private final Duration timeout;

    /** @param languages коды языков Google ({@code ru}, {@code en}…), из которых получаются голоса */
    public GoogleTranslateTts(Collection<String> languages) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                DEFAULT_ENDPOINT, languages, DEFAULT_TIMEOUT);
    }

    /** Адрес и таймаут меняются только в тестах: там вместо Google — локальный HTTP-сервер. */
    GoogleTranslateTts(HttpClient httpClient, String endpoint, Collection<String> languages, Duration timeout) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.voices = new LinkedHashSet<>(languages).stream()
                .map(language -> new VoiceInfo(new Voice(ID, language), languageName(language)))
                .toList();
        this.timeout = timeout;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Google";
    }

    @Override
    public List<VoiceInfo> voices() {
        return voices;
    }

    @Override
    public int maxTextLength() {
        return MAX_TEXT_LENGTH;
    }

    @Override
    public byte[] synthesize(String text, String voiceId) throws IOException, InterruptedException {
        URI uri = URI.create(endpoint + "?ie=UTF-8&total=1&idx=0&client=tw-ob"
                + "&tl=" + URLEncoder.encode(voiceId, StandardCharsets.UTF_8)
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

    /** «ru» → «Русский»; незнакомый Java код остаётся как есть. */
    static String languageName(String language) {
        String name = Locale.forLanguageTag(language).getDisplayLanguage(NAMES_LOCALE);
        return name.isEmpty() ? language : name.substring(0, 1).toUpperCase(NAMES_LOCALE) + name.substring(1);
    }
}
