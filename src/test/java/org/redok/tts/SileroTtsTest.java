package org.redok.tts;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Вместо контейнера Silero — локальный HTTP-сервер из JDK: тесты не требуют Docker. */
class SileroTtsTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicInteger ttsStatus = new AtomicInteger(200);
    private final AtomicInteger healthStatus = new AtomicInteger(200);
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheTextAndSpeakerAndReturnsTheAudio() throws Exception {
        SileroTts tts = engine(startServer());

        byte[] wav = tts.synthesize("скажи \"привет\"\nвсем", "aidar");

        assertArrayEquals("RIFF".getBytes(StandardCharsets.UTF_8), wav);
        assertEquals("{\"text\":\"скажи \\\"привет\\\"\\nвсем\",\"speaker\":\"aidar\"}", requestBody.get());
    }

    @Test
    void isUnavailableUntilTheFirstHealthCheck() throws Exception {
        SileroTts tts = engine(startServer());

        assertFalse(tts.isAvailable());
        tts.checkHealth();
        assertTrue(tts.isAvailable());
    }

    @Test
    void aFailingHealthCheckMakesItUnavailableAndARecoveryBringsItBack() throws Exception {
        SileroTts tts = engine(startServer());
        tts.checkHealth();

        healthStatus.set(503);
        tts.checkHealth();
        assertFalse(tts.isAvailable());

        healthStatus.set(200);
        tts.checkHealth();
        assertTrue(tts.isAvailable());
    }

    @Test
    void aStoppedServiceIsUnavailable() throws Exception {
        SileroTts tts = engine(startServer());
        tts.checkHealth();

        server.stop(0);
        tts.checkHealth();

        assertFalse(tts.isAvailable());
    }

    @Test
    void aNetworkErrorWhileSynthesizingMakesItUnavailableAtOnce() throws Exception {
        URI url = startServer();
        SileroTts tts = engine(url);
        tts.checkHealth();
        server.stop(0);

        assertThrows(IOException.class, () -> tts.synthesize("привет", "xenia"));
        assertFalse(tts.isAvailable(), "не ждём следующей проверки /health");
    }

    @Test
    void aRejectedTextIsAnErrorButTheServiceStaysAvailable() throws Exception {
        SileroTts tts = engine(startServer());
        tts.checkHealth();
        ttsStatus.set(400);

        IOException e = assertThrows(IOException.class, () -> tts.synthesize("🙂", "xenia"));

        assertTrue(e.getMessage().contains("HTTP 400") && e.getMessage().contains("nothing to say"), e.getMessage());
        assertTrue(tts.isAvailable(), "одни эмодзи — не повод выключать голоса Silero");
    }

    @Test
    void aTrailingSlashInTheUrlDoesNotBreakPaths() throws Exception {
        URI url = startServer();
        SileroTts tts = engine(URI.create(url + "/"));

        tts.synthesize("привет", "xenia");

        assertEquals("{\"text\":\"привет\",\"speaker\":\"xenia\"}", requestBody.get());
    }

    @Test
    void hasFiveRussianVoices() {
        SileroTts tts = new SileroTts(URI.create("http://127.0.0.1:1"));

        assertEquals(5, tts.voices().size());
        assertTrue(tts.voices().stream().allMatch(info -> info.voice().provider().equals("silero")));
        assertEquals(500, tts.maxTextLength());
    }

    @Test
    void escapesControlCharactersInJson() {
        assertEquals("\"a\\\\b\\t\\u0001\"", SileroTts.jsonString("a\\b\t\u0001"));
    }

    private SileroTts engine(URI url) {
        return new SileroTts(HttpClient.newHttpClient(), url, Duration.ofSeconds(5), Duration.ofHours(1));
    }

    private URI startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tts", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = (ttsStatus.get() == 200 ? "RIFF" : "BadRequest: nothing to say after normalization")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ttsStatus.get(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(healthStatus.get(), -1);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
