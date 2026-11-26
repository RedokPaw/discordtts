package org.redok.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redok.tts.Voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotConfigTest {

    @TempDir
    Path dir;

    @Test
    void envWinsOverTheFileAndTheFileOverDefaults() throws IOException {
        Path file = file("token=file-token\nport=9000\ntts.threads=7\n");

        BotConfig config = BotConfig.load(Map.of("PORT", "8080", "TTS_LANGUAGE", "en"), file);

        assertEquals("file-token", config.token());  // файл
        assertEquals(8080, config.port());           // env сильнее файла
        assertEquals(new Voice("google", "en"), config.ttsDefaultVoice()); // env, старое TTS_LANGUAGE
        assertEquals(7, config.ttsThreads());        // файл
        assertEquals(10, config.ttsQueueCapacity()); // по умолчанию
        assertEquals(3, config.ttsMaxChunks());      // по умолчанию
    }

    @Test
    void aMissingTokenNamesTheFileToPutItIn() {
        Path missing = dir.resolve("bot.properties");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> BotConfig.load(Map.of(), missing));
        assertTrue(e.getMessage().contains(missing.toAbsolutePath().toString()), e.getMessage());
    }

    @Test
    void rejectsANonNumericValue() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> BotConfig.load(Map.of("TOKEN", "t", "TTS_THREADS", "five"), dir.resolve("none")));
        assertTrue(e.getMessage().contains("TTS_THREADS"), e.getMessage());
    }

    @Test
    void rejectsMoreChunksPerMessageThanTheQueueHolds() {
        assertThrows(IllegalArgumentException.class,
                () -> BotConfig.load(Map.of("TOKEN", "t", "TTS_QUEUE_CAPACITY", "2"), dir.resolve("none")));
    }

    @Test
    void toStringDoesNotLeakTheToken() {
        BotConfig config = new BotConfig("secret-token", 8080, new Voice("google", "ru"), 5, 10, 3);

        assertFalse(config.toString().contains("secret-token"), config.toString());
    }

    private Path file(String content) throws IOException {
        Path file = dir.resolve("bot.properties");
        Files.writeString(file, content);
        return file;
    }
}
