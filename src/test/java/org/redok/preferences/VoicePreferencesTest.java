package org.redok.preferences;

import org.junit.jupiter.api.Test;
import org.redok.storage.StorageException;
import org.redok.testutil.FakeTts;
import org.redok.testutil.MemoryVoiceStore;
import org.redok.tts.Voice;
import org.redok.tts.VoiceRegistry;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoicePreferencesTest {

    private final FakeTts fake = new FakeTts(200);
    private final FakeTts other = new FakeTts("other", 200);
    private final VoiceRegistry voices = new VoiceRegistry(List.of(fake, other), FakeTts.VOICE);
    private final MemoryVoiceStore store = new MemoryVoiceStore();
    private final VoicePreferences preferences = new VoicePreferences(store, voices);

    @Test
    void aUserWithoutAChoiceSpeaksWithTheDefaultVoice() {
        assertEquals(FakeTts.VOICE, preferences.voiceFor("1"));
        assertEquals(Optional.empty(), preferences.chosen("1"));
    }

    @Test
    void aChosenVoiceIsSavedAndUsed() {
        preferences.set("1", FakeTts.OTHER_VOICE);

        assertEquals(FakeTts.OTHER_VOICE, preferences.voiceFor("1"));
        assertEquals(Optional.of(FakeTts.OTHER_VOICE), store.find("1"), "выбор в базе");
        assertEquals(FakeTts.VOICE, preferences.voiceFor("2"), "у других пользователей свой голос");
    }

    @Test
    void readsTheStoreOncePerUserEvenWhenThereIsNoChoice() {
        preferences.voiceFor("1");
        preferences.voiceFor("1");
        preferences.chosen("1");

        assertEquals(1, store.finds());
    }

    @Test
    void setAndResetUpdateTheCache() {
        preferences.voiceFor("1"); // в кэше «не выбирал»

        preferences.set("1", FakeTts.OTHER_VOICE);
        assertEquals(FakeTts.OTHER_VOICE, preferences.voiceFor("1"));

        preferences.reset("1");
        assertEquals(FakeTts.VOICE, preferences.voiceFor("1"));
        assertEquals(Optional.empty(), store.find("1"));
    }

    @Test
    void loadsAChoiceSavedBeforeTheRestart() {
        store.save("1", FakeTts.OTHER_VOICE);

        assertEquals(FakeTts.OTHER_VOICE, preferences.voiceFor("1"));
    }

    @Test
    void refusesAnUnknownVoice() {
        assertThrows(IllegalArgumentException.class, () -> preferences.set("1", new Voice("fake", "zzz")));
        assertThrows(IllegalArgumentException.class, () -> preferences.set("1", new Voice("silero", "xenia")));
        assertEquals(Optional.empty(), store.find("1"));
    }

    @Test
    void anUnavailableVoiceFallsBackToTheDefaultButStaysChosen() {
        Voice otherVoice = new Voice("other", "b");
        preferences.set("1", otherVoice);
        other.setAvailable(false);

        assertEquals(FakeTts.VOICE, preferences.voiceFor("1"));
        assertEquals(Optional.of(otherVoice), preferences.chosen("1"), "выбор не потерян");

        other.setAvailable(true);
        assertEquals(otherVoice, preferences.voiceFor("1"), "провайдер вернулся — вернулся и голос");
    }

    @Test
    void aVoiceThatLeftTheCatalogFallsBackToTheDefault() {
        store.save("1", new Voice("silero", "xenia")); // провайдер был, а теперь выключен в конфиге

        assertEquals(FakeTts.VOICE, preferences.voiceFor("1"));
    }

    @Test
    void aBrokenDatabaseDoesNotStopTheBotFromSpeaking() {
        store.breakDown();

        assertEquals(FakeTts.VOICE, preferences.voiceFor("1"));
        assertThrows(StorageException.class, () -> preferences.set("1", FakeTts.OTHER_VOICE),
                "а вот о неудачном сохранении пользователь должен узнать");
    }

    @Test
    void aFailedSaveDoesNotChangeTheCache() {
        store.breakDown();
        assertThrows(StorageException.class, () -> preferences.set("1", FakeTts.OTHER_VOICE));

        assertEquals(FakeTts.VOICE, preferences.voiceFor("1"));
    }
}
