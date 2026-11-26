package org.redok.storage;

import org.redok.tts.Voice;

import java.util.Optional;

/** Выбранные пользователями голоса. Один голос на пользователя, общий для всех серверов. */
public interface UserVoiceStore {

    /** Голос, который пользователь выбрал, или пусто, если не выбирал. */
    Optional<Voice> find(String userId);

    /** Сохраняет или заменяет голос пользователя. */
    void save(String userId, Voice voice);

    /** Удаляет выбор пользователя. */
    void delete(String userId);
}
