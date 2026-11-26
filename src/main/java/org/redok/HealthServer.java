package org.redok;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** HTTP-эндпоинт /health для healthcheck в docker-compose. */
final class HealthServer {

    private static final Logger log = LoggerFactory.getLogger(HealthServer.class);
    private static final byte[] BODY = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);

    private HealthServer() {
    }

    static HttpServer start(int port) throws IOException {
        log.info("Starting health check server on port {}", port);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, BODY.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(BODY);
            }
        });
        server.start();
        return server;
    }
}
