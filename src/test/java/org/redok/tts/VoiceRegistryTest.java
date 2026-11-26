package org.redok.tts;

import org.junit.jupiter.api.Test;
import org.redok.testutil.FakeTts;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceRegistryTest {

    private final FakeTts fake = new FakeTts(200);           // fake:a «Альфа», fake:b «Бета»
    private final FakeTts other = new FakeTts("other", 200); // other:a «Альфа», other:b «Бета»
    private final VoiceRegistry voices = new VoiceRegistry(List.of(fake, other), FakeTts.VOICE);

    @Test
    void parsesAndPrintsVoiceKeys() {
        assertEquals(new Voice("google", "ru"), Voice.parse("google:ru"));
        assertEquals("google:ru", new Voice("google", "ru").key());
        assertThrows(IllegalArgumentException.class, () -> Voice.parse("ru"));
        assertThrows(IllegalArgumentException.class, () -> Voice.parse("google:"));
    }

    @Test
    void rejectsTwoEnginesWithTheSameId() {
        assertThrows(IllegalArgumentException.class,
                () -> new VoiceRegistry(List.of(fake, new FakeTts(10)), FakeTts.VOICE));
    }

    @Test
    void rejectsAnUnknownDefaultVoice() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new VoiceRegistry(List.of(fake), new Voice("fake", "zzz")));
        assertTrue(e.getMessage().contains("fake:a"), "в ошибке перечислены настоящие голоса: " + e.getMessage());
    }

    @Test
    void findsOnlyKnownVoices() {
        assertEquals("Бета", voices.find(new Voice("other", "b")).orElseThrow().name());
        assertEquals(Optional.empty(), voices.find(new Voice("fake", "zzz")), "неизвестный голос известного провайдера");
        assertEquals(Optional.empty(), voices.find(new Voice("silero", "a")), "неизвестный провайдер");
    }

    @Test
    void givesTheEngineOfAVoice() {
        assertSame(other, voices.engine(new Voice("other", "a")));
        assertThrows(IllegalArgumentException.class, () -> voices.engine(new Voice("silero", "a")));
    }

    @Test
    void availabilityComesFromTheEngine() {
        assertTrue(voices.isAvailable(new Voice("other", "a")));
        other.setAvailable(false);
        assertFalse(voices.isAvailable(new Voice("other", "a")));
        assertFalse(voices.isAvailable(new Voice("fake", "zzz")), "неизвестный голос недоступен");
    }

    @Test
    void searchIgnoresCaseAndPutsPrefixMatchesFirst() {
        assertEquals(List.of("fake:b", "other:b"), keys(voices.search("бЕт", 25)));
        // «а» — начало имени «Альфа» и id «a»; «Бета» содержит «а» только внутри, поэтому она после
        assertEquals(List.of("fake:a", "other:a", "fake:b", "other:b"), keys(voices.search("а", 25)));
        assertEquals(List.of("other:a", "other:b"), keys(voices.search("other", 25)), "по провайдеру");
    }

    @Test
    void searchReturnsEverythingForAnEmptyQueryButNoMoreThanTheLimit() {
        assertEquals(4, voices.search("", 25).size());
        assertEquals(List.of("fake:a", "fake:b"), keys(voices.search(" ", 2)));
        assertEquals(List.of(), voices.search("нет такого", 25));
    }

    @Test
    void resolvesWhatAPersonTyped() {
        assertEquals("other:b", voices.resolve("other:b").orElseThrow().voice().key());
        assertEquals(Optional.empty(), voices.resolve("Бета (Fake)"), "оба провайдера называются Fake");
        assertEquals(Optional.empty(), voices.resolve("бета"), "«Бета» есть у двух провайдеров");
        VoiceRegistry single = fake.registry();
        assertEquals("fake:b", single.resolve(" бета ").orElseThrow().voice().key());
        assertEquals("fake:b", single.resolve("b").orElseThrow().voice().key());
        assertEquals(Optional.empty(), single.resolve("fake:zzz"));
    }

    @Test
    void labelsAVoiceWithItsProvider() {
        assertEquals("Альфа (Fake)", voices.label(voices.find(FakeTts.VOICE).orElseThrow()));
    }

    private static List<String> keys(List<VoiceInfo> infos) {
        return infos.stream().map(info -> info.voice().key()).toList();
    }
}
