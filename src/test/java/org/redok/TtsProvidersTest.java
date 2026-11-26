package org.redok;

import org.junit.jupiter.api.Test;
import org.redok.configuration.BotConfig;
import org.redok.tts.TtsEngine;
import org.redok.tts.Voice;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsProvidersTest {

    @Test
    void createsOnlyTheListedProvidersInOrder() {
        List<TtsEngine> engines = TtsProviders.create(config(List.of("silero", "google"), URI.create("http://127.0.0.1:1")));

        assertEquals(List.of("silero", "google"), engines.stream().map(TtsEngine::id).toList());
    }

    @Test
    void withoutSileroInTheListItsUrlIsNotNeeded() {
        List<TtsEngine> engines = TtsProviders.create(config(List.of("google"), null));

        assertEquals(List.of("google"), engines.stream().map(TtsEngine::id).toList());
    }

    @Test
    void rejectsAnUnknownProviderAndNamesTheKnownOnes() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TtsProviders.create(config(List.of("google", "silro"), null)));
        assertTrue(e.getMessage().contains("silro") && e.getMessage().contains("silero"), e.getMessage());
    }

    @Test
    void sileroNeedsItsUrl() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TtsProviders.create(config(List.of("google", "silero"), null)));
        assertTrue(e.getMessage().contains("SILERO_URL"), e.getMessage());
    }

    @Test
    void theDefaultGoogleLanguageIsAlwaysAVoice() {
        BotConfig config = new BotConfig("t", 8080, new Voice("google", "pt"), 5, 10, 3, Path.of("bot.db"),
                List.of("google"), null);

        TtsEngine google = TtsProviders.create(config).getFirst();

        assertTrue(google.voices().stream().anyMatch(info -> info.voice().equals(new Voice("google", "pt"))));
    }

    private static BotConfig config(List<String> providers, URI sileroUrl) {
        return new BotConfig("t", 8080, new Voice("google", "ru"), 5, 10, 3, Path.of("bot.db"), providers, sileroUrl);
    }
}
