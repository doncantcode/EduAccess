package com.eduaccess.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates eduaccess.db and its schema on startup. Safe to call on every boot: the schema uses IF NOT EXISTS.
 */
public final class DatabaseInitializer {

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final String SEED_RESOURCE = "/db/seed.sql";

    private DatabaseInitializer() {
    }

    /**
     * @param dbFile       path of the SQLite file, created with its folders if missing
     * @param loadDemoSeed load the demo data, but only when the users table is empty
     */
    public static void initialize(Path dbFile, boolean loadDemoSeed) throws SQLException, IOException {
        Files.createDirectories(dbFile.toAbsolutePath().getParent());
        DatabaseManager db = new DatabaseManager(dbFile);
        try (Connection connection = db.openConnection()) {
            runScript(connection, SCHEMA_RESOURCE);
            convertRetiredRoles(connection);
            if (loadDemoSeed && isTableEmpty(connection, "users")) {
                runScript(connection, SEED_RESOURCE);
            }
        }
    }

    /**
     * EduAccess has two kinds of user, students and library admins. Accounts created by earlier builds with the
     * Faculty or Donor role become students. The statement does nothing once no such rows remain.
     */
    private static void convertRetiredRoles(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE users SET role = 'Student' WHERE role IN ('Faculty', 'Donor')");
        }
    }

    private static void runScript(Connection connection, String resource) throws SQLException, IOException {
        List<String> statements = splitStatements(readResource(resource));
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** Drops whole-line "--" comments and splits on ';'. Scripts must not put ';' inside a comment or a string. */
    static List<String> splitStatements(String script) {
        String withoutComments = script.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(sql -> !sql.isEmpty())
                .toList();
    }

    private static boolean isTableEmpty(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() && rows.getLong(1) == 0;
        }
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = DatabaseInitializer.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing classpath resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
