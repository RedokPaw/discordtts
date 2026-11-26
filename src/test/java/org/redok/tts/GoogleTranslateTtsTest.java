package org.redok.tts;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Вместо Google — локальный HTTP-сервер из JDK: тесты не ходят в сеть. */
class GoogleTranslateTtsTest {

    private final ExecutorService serverThreads = Executors.newCachedThreadPool();
    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        serverThreads.shutdownNow();
    }

    @Test
    void returnsTheAudioAndSendsLanguageAndEncodedText() throws Exception {
        byte[] mp3 = {1, 2, 3};
        GoogleTranslateTts tts = engine(200, mp3, Duration.ZERO, Duration.ofSeconds(5));

        assertArrayEquals(mp3, tts.synthesize("привет, мир & co"));

        Map<String, String> query = parse(rawQuery.get());
        assertEquals("ru", query.get("tl"));
        assertEquals("привет, мир & co", query.get("q"));
        assertEquals("tw-ob", query.get("client"));
    }

    @Test
    void failsOnAnErrorStatus() throws Exception {
        GoogleTranslateTts tts = engine(400, "<html>".getBytes(StandardCharsets.UTF_8), Duration.ZERO, Duration.ofSeconds(5));

        IOException e = assertThrows(IOException.class, () -> tts.synthesize("а".repeat(201)));
        assertTrue(e.getMessage().contains("HTTP 400"), e.getMessage());
    }

    @Test
    void givesUpAfterTheTimeout() throws Exception {
        GoogleTranslateTts tts = engine(200, new byte[]{1}, Duration.ofSeconds(3), Duration.ofMillis(300));
        long start = System.nanoTime();

        assertThrows(HttpTimeoutException.class, () -> tts.synthesize("привет"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2000, "ждали дольше таймаута");
    }

    @Test
    void acceptsAtMost200Characters() {
        assertEquals(200, new GoogleTranslateTts("ru").maxTextLength());
    }

    private GoogleTranslateTts engine(int status, byte[] body, Duration delay, Duration timeout) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate_tts", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(serverThreads);
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/translate_tts";
        return new GoogleTranslateTts(HttpClient.newHttpClient(), endpoint, "ru", timeout);
    }

    private static Map<String, String> parse(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] keyValue = pair.split("=", 2);
            query.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
        }
        return query;
    }
}
