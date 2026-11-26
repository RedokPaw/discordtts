package org.redok.storage;

import org.redok.tts.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** {@link UserVoiceStore} в таблице {@code user_voice}. */
public class SqliteUserVoiceStore implements UserVoiceStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteUserVoiceStore.class);
    private final Database database;

    public SqliteUserVoiceStore(Database database) {
        this.database = database;
    }

    @Override
    public Optional<Voice> find(String userId) {
        return database.query("find voice of " + userId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT provider, voice FROM user_voice WHERE user_id = ?")) {
                statement.setString(1, userId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    try {
                        return Optional.of(new Voice(rs.getString("provider"), rs.getString("voice")));
                    } catch (IllegalArgumentException e) {
                        log.warn("user {} has a broken voice row, ignoring it: {}", userId, e.getMessage());
                        return Optional.empty();
                    }
                }
            }
        });
    }

    @Override
    public void save(String userId, Voice voice) {
        database.query("save voice of " + userId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_voice (user_id, provider, voice, updated_at) VALUES (?, ?, ?, ?)
                    ON CONFLICT (user_id) DO UPDATE
                    SET provider = excluded.provider, voice = excluded.voice, updated_at = excluded.updated_at""")) {
                statement.setString(1, userId);
                statement.setString(2, voice.provider());
                statement.setString(3, voice.id());
                statement.setLong(4, System.currentTimeMillis());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void delete(String userId) {
        database.query("delete voice of " + userId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM user_voice WHERE user_id = ?")) {
                statement.setString(1, userId);
                return statement.executeUpdate();
            }
        });
    }
}
