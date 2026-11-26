package org.redok.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Файл SQLite и одно соединение с ним. Пишем редко (только из /voice), поэтому одного соединения
 * под блокировкой хватает. Схема обновляется при открытии по {@code PRAGMA user_version}.
 */
public final class Database implements AutoCloseable {

    /** Шаги схемы по порядку. Добавлять только в конец: применённый шаг больше не запустится. */
    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE user_voice (
                user_id    TEXT PRIMARY KEY,
                provider   TEXT NOT NULL,
                voice      TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )""");

    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private final Connection connection;

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Открывает файл (создаёт его и папку, если нет) и доводит схему до текущей версии. */
    public static Database open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL"); // запись не блокирует чтение, файл переживает падение
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            migrate(connection);
            log.info("database opened: {}", file.toAbsolutePath());
            return new Database(connection);
        } catch (SQLException | IOException e) {
            throw new StorageException("could not open database " + file.toAbsolutePath(), e);
        }
    }

    /** Выполняет работу с соединением под блокировкой; SQLException превращается в {@link StorageException}. */
    public synchronized <T> T query(String what, SqlWork<T> work) {
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw new StorageException("database error: " + what, e);
        }
    }

    static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    private static void migrate(Connection connection) throws SQLException {
        int version = schemaVersion(connection);
        if (version > MIGRATIONS.size()) {
            // база от более новой версии бота: старый код может её испортить
            throw new StorageException("database schema v" + version + " is newer than this bot knows (v"
                    + MIGRATIONS.size() + ")");
        }
        for (int step = version; step < MIGRATIONS.size(); step++) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute(MIGRATIONS.get(step));
                statement.execute("PRAGMA user_version = " + (step + 1)); // в той же транзакции, что и шаг
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
            log.info("database schema migrated to v{}", step + 1);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("could not close the database", e);
        }
    }
}
