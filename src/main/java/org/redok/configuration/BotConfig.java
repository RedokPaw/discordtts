package org.redok.configuration;

import org.redok.tts.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Настройки бота. Каждое значение ищется в переменной окружения, затем в bot.properties
 * в рабочей директории, затем берётся значение по умолчанию.
 */
public record BotConfig(String token, int port, Voice ttsDefaultVoice, int ttsThreads, int ttsQueueCapacity,
                        int ttsMaxChunks) {

    private static final Logger log = LoggerFactory.getLogger(BotConfig.class);
    // Файл ищется в рабочей директории, а не в classpath: из src/main/resources он попадал в jar и Docker-образ.
    private static final Path PROPERTIES_FILE = Path.of("bot.properties");

    public BotConfig {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1..65535, got " + port);
        }
        Objects.requireNonNull(ttsDefaultVoice, "ttsDefaultVoice");
        requirePositive("ttsThreads", ttsThreads);
        requirePositive("ttsQueueCapacity", ttsQueueCapacity);
        requirePositive("ttsMaxChunks", ttsMaxChunks);
        if (ttsMaxChunks > ttsQueueCapacity) {
            // иначе сообщение из ttsMaxChunks кусков не влезет даже в пустую очередь
            throw new IllegalArgumentException("ttsMaxChunks (" + ttsMaxChunks + ") must not exceed ttsQueueCapacity ("
                    + ttsQueueCapacity + ")");
        }
    }

    public static BotConfig load() {
        return load(System.getenv(), PROPERTIES_FILE);
    }

    static BotConfig load(Map<String, String> env, Path propertiesFile) {
        log.info("Loading configuration");
        Source source = new Source(env, loadFromFile(propertiesFile));

        String token = source.get("TOKEN", "token");
        if (token == null) {
            throw new IllegalStateException("Bot token not found: set the TOKEN environment variable or put token=... into "
                    + propertiesFile.toAbsolutePath());
        }
        BotConfig config = new BotConfig(
                token,
                source.getInt("PORT", "port", 8081),
                defaultVoice(source),
                source.getInt("TTS_THREADS", "tts.threads", 5),
                source.getInt("TTS_QUEUE_CAPACITY", "tts.queue-capacity", 10),
                source.getInt("TTS_MAX_CHUNKS", "tts.max-chunks", 3));
        log.info("Configuration loaded: {}", config);
        return config;
    }

    /** Без токена: конфигурация пишется в лог. */
    @Override
    public String toString() {
        return "BotConfig[port=%d, ttsDefaultVoice=%s, ttsThreads=%d, ttsQueueCapacity=%d, ttsMaxChunks=%d]"
                .formatted(port, ttsDefaultVoice, ttsThreads, ttsQueueCapacity, ttsMaxChunks);
    }

    /** TTS_DEFAULT_VOICE, а без него — google:TTS_LANGUAGE, чтобы старые .env работали как раньше. */
    private static Voice defaultVoice(Source source) {
        String voice = source.get("TTS_DEFAULT_VOICE", "tts.default-voice");
        if (voice == null) {
            return new Voice("google", source.get("TTS_LANGUAGE", "tts.language", "ru").strip());
        }
        try {
            return Voice.parse(voice);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TTS_DEFAULT_VOICE / tts.default-voice must look like provider:id "
                    + "(for example google:ru), got '" + voice + "'");
        }
    }

    private static Properties loadFromFile(Path propertiesFile) {
        if (BotConfig.class.getClassLoader().getResource("bot.properties") != null) {
            log.warn("bot.properties found in classpath and ignored: from there it gets packed into the jar with the token. "
                    + "Move it to {}", propertiesFile.toAbsolutePath());
        }
        Properties props = new Properties();
        if (Files.notExists(propertiesFile)) {
            return props;
        }
        try (Reader reader = Files.newBufferedReader(propertiesFile)) {
            props.load(reader);
        } catch (IOException e) {
            log.warn("Could not read {}", propertiesFile.toAbsolutePath(), e);
        }
        return props;
    }

    private static void requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, got " + value);
        }
    }

    /** Порядок поиска значения: переменная окружения, затем bot.properties. */
    private record Source(Map<String, String> env, Properties file) {

        String get(String envKey, String fileKey) {
            String value = env.get(envKey);
            return value != null ? value : file.getProperty(fileKey);
        }

        String get(String envKey, String fileKey, String defaultValue) {
            String value = get(envKey, fileKey);
            return value != null ? value : defaultValue;
        }

        int getInt(String envKey, String fileKey, int defaultValue) {
            String value = get(envKey, fileKey);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.strip());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(envKey + " / " + fileKey + " must be an integer, got '" + value + "'");
            }
        }
    }
}
