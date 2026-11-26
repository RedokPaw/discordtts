package org.redok.preferences;

import org.redok.storage.StorageException;
import org.redok.storage.UserVoiceStore;
import org.redok.tts.Voice;
import org.redok.tts.VoiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Какой голос у пользователя. Выбор хранится в {@link UserVoiceStore} и кэшируется в памяти, чтобы
 * не ходить в базу на каждое сообщение; кэш обновляется при каждом изменении — других писателей
 * у базы нет. Пустой выбор тоже кэшируется. Кэш не ограничен: запись — пара строк на пользователя.
 */
public class VoicePreferences {

    private static final Logger log = LoggerFactory.getLogger(VoicePreferences.class);
    private final UserVoiceStore store;
    private final VoiceRegistry voices;
    private final Map<String, Optional<Voice>> cache = new ConcurrentHashMap<>();

    public VoicePreferences(UserVoiceStore store, VoiceRegistry voices) {
        this.store = store;
        this.voices = voices;
    }

    /**
     * Голос, которым читать сообщения пользователя: выбранный, если он сейчас может звучать, иначе голос
     * по умолчанию. Недоступный голос из базы не удаляется: провайдер может вернуться. Ошибка базы
     * не мешает читать чат — тогда тоже голос по умолчанию.
     */
    public Voice voiceFor(String userId) {
        Optional<Voice> chosen;
        try {
            chosen = chosen(userId);
        } catch (StorageException e) {
            log.warn("could not read the voice of user {}, using the default voice", userId, e);
            return voices.defaultVoice();
        }
        if (chosen.isEmpty()) {
            return voices.defaultVoice();
        }
        if (!voices.isAvailable(chosen.get())) {
            log.debug("voice {} of user {} is not available now, using the default voice {}",
                    chosen.get(), userId, voices.defaultVoice());
            return voices.defaultVoice();
        }
        return chosen.get();
    }

    /** Что пользователь выбрал сам, без подстановки голоса по умолчанию. */
    public Optional<Voice> chosen(String userId) {
        return cache.computeIfAbsent(userId, store::find);
    }

    /** Сохраняет выбор; голоса, которого нет в каталоге, бросает {@link IllegalArgumentException}. */
    public void set(String userId, Voice voice) {
        if (voices.find(voice).isEmpty()) {
            throw new IllegalArgumentException("unknown voice " + voice);
        }
        store.save(userId, voice);
        cache.put(userId, Optional.of(voice)); // после записи: если база упала, кэш не врёт
        log.info("user {} chose voice {}", userId, voice);
    }

    /** Забывает выбор: дальше пользователь говорит голосом по умолчанию. */
    public void reset(String userId) {
        store.delete(userId);
        cache.put(userId, Optional.empty());
        log.info("user {} reset the voice", userId);
    }
}
