package com.university.registration.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteStoreTest {

    @TempDir
    Path dir;

    private SqliteStore store() {
        return new SqliteStore(dir.resolve("nested/test.db"),
                "CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
    }

    @Test
    void insertReturnsGeneratedKeysAndDataSurvivesReopen() {
        assertEquals(1, store().insert("INSERT INTO items (name) VALUES (?)", "first"));
        assertEquals(2, store().insert("INSERT INTO items (name) VALUES (?)", "second"));

        List<String> names = store().list("SELECT name FROM items ORDER BY id", row -> row.getString(1));

        assertEquals(List.of("first", "second"), names);
    }

    @Test
    void failedTransactionIsRolledBack() {
        SqliteStore store = store();

        assertThrows(IllegalStateException.class, () -> store.inTransaction(session -> {
            session.insert("INSERT INTO items (name) VALUES (?)", "ghost");
            throw new IllegalStateException("boom");
        }));

        assertTrue(store.first("SELECT name FROM items", row -> row.getString(1)).isEmpty());
    }

    @Test
    void sqlErrorsSurfaceAsStorageException() {
        SqliteStore store = store();

        assertThrows(StorageException.class, () -> store.execute("INSERT INTO items (name) VALUES (NULL)"));
        assertThrows(StorageException.class, () -> store.list("SELECT nope FROM items", row -> row.getString(1)));
    }
}
