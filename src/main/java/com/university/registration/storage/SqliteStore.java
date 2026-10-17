package com.university.registration.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class SqliteStore {

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    private final String url;

    public SqliteStore(Path file, String... schema) {
        Path absolute = file.toAbsolutePath();
        try {
            Files.createDirectories(absolute.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.url = "jdbc:sqlite:" + absolute;
        inTransaction(session -> {
            for (String ddl : schema) {
                session.execute(ddl);
            }
            return null;
        });
    }

    public long insert(String sql, Object... params) {
        return inTransaction(session -> session.insert(sql, params));
    }

    public int execute(String sql, Object... params) {
        return inTransaction(session -> session.execute(sql, params));
    }

    public <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
        return inTransaction(session -> session.list(sql, mapper, params));
    }

    public <T> Optional<T> first(String sql, RowMapper<T> mapper, Object... params) {
        return inTransaction(session -> session.first(sql, mapper, params));
    }

    public <T> T inTransaction(Function<Session, T> work) {
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(new Session(connection));
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    public static final class Session {
        private final Connection connection;

        private Session(Connection connection) {
            this.connection = connection;
        }

        public long insert(String sql, Object... params) {
            execute(sql, params);
            try (Statement statement = connection.createStatement();
                 ResultSet key = statement.executeQuery("SELECT last_insert_rowid()")) {
                key.next();
                return key.getLong(1);
            } catch (SQLException e) {
                throw new StorageException(e);
            }
        }

        public int execute(String sql, Object... params) {
            try (PreparedStatement statement = prepare(sql, params)) {
                return statement.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException(e);
            }
        }

        public <T> List<T> list(String sql, RowMapper<T> mapper, Object... params) {
            try (PreparedStatement statement = prepare(sql, params);
                 ResultSet rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapper.map(rows));
                }
                return result;
            } catch (SQLException e) {
                throw new StorageException(e);
            }
        }

        public <T> Optional<T> first(String sql, RowMapper<T> mapper, Object... params) {
            return list(sql, mapper, params).stream().findFirst();
        }

        private PreparedStatement prepare(String sql, Object... params) throws SQLException {
            PreparedStatement statement = connection.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            return statement;
        }
    }
}
