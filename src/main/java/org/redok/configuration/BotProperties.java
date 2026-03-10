package org.redok.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Properties;

public class BotProperties {

    private final Logger log = LoggerFactory.getLogger(BotProperties.class);

    private int port = 8081;

    private String token;

    private BotProperties() {
    }

    public String getToken() {
        return token;
    }

    public int getPort() {
        return port;
    }

    //TODO: Обязательно отрефакторить эту кашу
    private void loadTokenFromProperties() throws FileNotFoundException {
        log.info("Loading configuration");
        String envToken = System.getenv("TOKEN");
        String envPort = System.getenv("PORT");
        token = envToken;
        if (envPort != null) {
            port = Integer.parseInt(envPort);
        } else {
            log.info("Using standard healthcheck port: 8081");
        }
        if (envToken != null) {
            log.info("Using environment variable token");
            return;
        } else {
            log.warn("Bot token environment variable not set, trying to find bot.properties");
            Properties prop = new Properties();
            try (InputStream input = BotProperties.class.getClassLoader().getResourceAsStream("bot.properties")) {
                log.info("Properties file found");
                prop.load(input);
                token = prop.getProperty("token");
                String portProperty = prop.getProperty("port");
                if (portProperty == null) {
                    log.info("Using standard healthcheck port: 8081");
                } else {
                    port = Integer.parseInt(portProperty);
                }
            } catch (Exception e) {
                log.error("Error while loading bot.properties!", e);
                throw new FileNotFoundException(e.getMessage());
            }
        }
        log.info("Configuration loaded");
    }

    public static BotProperties getDefaultProperties() throws FileNotFoundException {
        BotProperties bp = new BotProperties();
        bp.loadTokenFromProperties();
        return bp;
    }
}
