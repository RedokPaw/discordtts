package org.redok.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BotProperties {

    private static final int DEFAULT_PORT = 8081;
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
            throw new IllegalStateException("Bot token not found in env or bot.properties");
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
        Properties props = new Properties();
        try (InputStream input = BotProperties.class.getClassLoader().getResourceAsStream("bot.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            log.warn("Could not load bot.properties", e);
        }
        return props;
    }

    public static BotProperties getDefaultProperties() {
        BotProperties bp = new BotProperties();
        bp.loadProperties();
        return bp;
    }
}
