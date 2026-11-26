package org.redok.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redok.tts.Voice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Настоящий SQLite во временном файле: проверяется и SQL, и миграции, и что данные переживают переоткрытие. */
class SqliteUserVoiceStoreTest {

    private static final Voice RU = new Voice("google", "ru");
    private static final Voice EN = new Voice("google", "en");

    @TempDir
    Path dir;
    private Database database;

    @AfterEach
    void close() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void createsTheFileAndItsFolder() {
        Path file = dir.resolve("nested/folder/bot.db");

        database = Database.open(file);

        assertTrue(Files.exists(file));
    }

    @Test
    void savesFindsReplacesAndDeletes() {
        UserVoiceStore store = new SqliteUserVoiceStore(database = Database.open(dir.resolve("bot.db")));

        assertEquals(Optional.empty(), store.find("1"));
        store.save("1", RU);
        assertEquals(Optional.of(RU), store.find("1"));
        store.save("1", EN);
        assertEquals(Optional.of(EN), store.find("1"), "второй save заменяет голос");
        store.delete("1");
        assertEquals(Optional.empty(), store.find("1"));
        store.delete("1"); // удалить несуществующее — не ошибка
    }

    @Test
    void usersDoNotSeeEachOthersVoices() {
        UserVoiceStore store = new SqliteUserVoiceStore(database = Database.open(dir.resolve("bot.db")));

        store.save("1", RU);
        store.save("2", EN);

        assertEquals(Optional.of(RU), store.find("1"));
        assertEquals(Optional.of(EN), store.find("2"));
    }

    @Test
    void voicesSurviveReopeningAndTheSchemaIsNotMigratedTwice() {
        Path file = dir.resolve("bot.db");
        Database first = Database.open(file);
        new SqliteUserVoiceStore(first).save("1", RU);
        first.close();

        database = Database.open(file); // второй CREATE TABLE упал бы

        assertEquals(Optional.of(RU), new SqliteUserVoiceStore(database).find("1"));
        assertEquals(Database.MIGRATIONS.size(),
                (int) database.query("version", Database::schemaVersion));
    }

    @Test
    void refusesADatabaseFromANewerBot() {
        Path file = dir.resolve("bot.db");
        Database newer = Database.open(file);
        newer.query("bump version", connection -> {
            try (Statement statement = connection.createStatement()) {
                return statement.execute("PRAGMA user_version = 999");
            }
        });
        newer.close();

        StorageException e = assertThrows(StorageException.class, () -> Database.open(file));
        assertTrue(e.getMessage().contains("newer"), e.getMessage());
    }

    @Test
    void ignoresABrokenRow() {
        database = Database.open(dir.resolve("bot.db"));
        database.query("insert broken row", connection -> {
            try (Statement statement = connection.createStatement()) {
                return statement.executeUpdate("INSERT INTO user_voice VALUES ('1', '', 'ru', 0)");
            }
        });

        assertEquals(Optional.empty(), new SqliteUserVoiceStore(database).find("1"));
    }
}
