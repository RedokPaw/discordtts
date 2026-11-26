package org.redok.testutil;

import org.redok.storage.StorageException;
import org.redok.storage.UserVoiceStore;
import org.redok.tts.Voice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Хранилище голосов в памяти: считает чтения и умеет «сломаться», как база. */
public final class MemoryVoiceStore implements UserVoiceStore {

    private final Map<String, Voice> voices = new ConcurrentHashMap<>();
    private final AtomicInteger finds = new AtomicInteger();
    private volatile boolean broken;

    /** Все следующие вызовы бросят {@link StorageException}. */
    public void breakDown() {
        broken = true;
    }

    /** Сколько раз читали из «базы». */
    public int finds() {
        return finds.get();
    }

    @Override
    public Optional<Voice> find(String userId) {
        finds.incrementAndGet();
        check();
        return Optional.ofNullable(voices.get(userId));
    }

    @Override
    public void save(String userId, Voice voice) {
        check();
        voices.put(userId, voice);
    }

    @Override
    public void delete(String userId) {
        check();
        voices.remove(userId);
    }

    private void check() {
        if (broken) {
            throw new StorageException("database is broken");
        }
    }
}
