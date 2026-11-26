package org.redok.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class BotProperties {

    private static final int DEFAULT_PORT = 8081;
    // Файл ищется в рабочей директории, а не в classpath: из src/main/resources он попадал в jar и Docker-образ.
    private static final Path PROPERTIES_FILE = Path.of("bot.properties");
    private final Logger log = LoggerFactory.getLogger(BotProperties.class);

    private int port = DEFAULT_PORT;
    private String token;

    private BotProperties() {
    }

    public String getToken() {
        return token;
    }

    public int getPort() {
        return port;
    }

    private void loadProperties() {
        log.info("Loading configuration");
        Properties fileProps = loadFromFile();

        token = resolveProperty("TOKEN", "token", fileProps);
        if (token == null) {
            throw new IllegalStateException("Bot token not found: set the TOKEN environment variable or put token=... into "
                    + PROPERTIES_FILE.toAbsolutePath());
        }

        String portValue = resolveProperty("PORT", "port", fileProps);
        port = portValue != null ? Integer.parseInt(portValue) : DEFAULT_PORT;

        log.info("Configuration loaded");
    }

    private String resolveProperty(String envKey, String fileKey, Properties fileProps) {
        String envValue = System.getenv(envKey);
        return envValue != null ? envValue : fileProps.getProperty(fileKey);
    }

    private Properties loadFromFile() {
        if (BotProperties.class.getClassLoader().getResource("bot.properties") != null) {
            log.warn("bot.properties found in classpath and ignored: from there it gets packed into the jar with the token. "
                    + "Move it to {}", PROPERTIES_FILE.toAbsolutePath());
        }
        Properties props = new Properties();
        if (Files.notExists(PROPERTIES_FILE)) {
            return props;
        }
        try (Reader reader = Files.newBufferedReader(PROPERTIES_FILE)) {
            props.load(reader);
        } catch (IOException e) {
            log.warn("Could not read {}", PROPERTIES_FILE.toAbsolutePath(), e);
        }
        return props;
    }

    public static BotProperties getDefaultProperties() {
        BotProperties bp = new BotProperties();
        bp.loadProperties();
        return bp;
    }
}
