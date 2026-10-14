package com.eduaccess.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens JDBC connections to the local SQLite file and runs work on them. */
public final class DatabaseManager {

    private final String jdbcUrl;

    public DatabaseManager(Path dbFile) {
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
    }

    /**
     * SQLite checks foreign keys only when the connection enables them, and the setting does not persist.
     * So every connection turns them on here.
     */
    public Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    /** Runs read-only work on a fresh connection. */
    public <T> T read(SqlWork<T> work) throws SQLException {
        try (Connection connection = openConnection()) {
            return work.apply(connection);
        }
    }

    /** Runs work in one transaction. Any exception rolls everything back. */
    public <T> T transaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
