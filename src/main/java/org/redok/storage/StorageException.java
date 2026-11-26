package org.redok.storage;

/** Ошибка базы данных: обёртка над {@link java.sql.SQLException}, чтобы не тащить checked-исключение наверх. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
