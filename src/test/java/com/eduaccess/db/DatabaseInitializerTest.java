package com.eduaccess.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.security.PasswordHasher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseInitializerTest {

    private static final String DEMO_PASSWORD = "Demo#2026";
    /** TEST ONLY: the temporary accounts from seed.sql. */
    private static final String TEST_STUDENT_ID = "test-student";
    private static final String TEST_STUDENT_PASSWORD = "123";
    private static final String TEST_ADMIN_ID = "123";
    private static final String TEST_ADMIN_PASSWORD = "admin";

    @TempDir
    Path tempDir;

    private Path dbFile;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("data").resolve("eduaccess.db");
    }

    @Test
    void createsTheFileAndEveryTableOnFirstBoot() throws Exception {
        DatabaseInitializer.initialize(dbFile, false);

        assertTrue(Files.exists(dbFile));
        assertTrue(tableNames().containsAll(List.of(
                "users", "physical_items", "physical_item_levels",
                "loans", "digital_resources", "digital_downloads")));
    }

    @Test
    void schemaCanBeAppliedAgainWithoutError() throws Exception {
        DatabaseInitializer.initialize(dbFile, false);
        DatabaseInitializer.initialize(dbFile, false);
    }

    @Test
    void demoSeedLoadsOnceEvenAfterRestart() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);
        assertEquals(14, queryInt("SELECT COUNT(*) FROM users"));

        DatabaseInitializer.initialize(dbFile, true);
        assertEquals(14, queryInt("SELECT COUNT(*) FROM users"));
    }

    @Test
    void seededLoanStatusesAgreeWithItemStatuses() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);

        int mismatches = queryInt("""
                SELECT COUNT(*) FROM loans l
                JOIN physical_items i ON i.item_id = l.item_id
                WHERE (l.status = 'Active' AND i.status <> 'Borrowed')
                   OR (l.status = 'Overdue' AND i.status <> 'Overdue')
                """);
        assertEquals(0, mismatches);
    }

    @Test
    void everySeededAccountAuthenticatesWithTheDocumentedPassword() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);

        int checked = 0;
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rows = s.executeQuery("SELECT school_id, password_hash FROM users")) {
            while (rows.next()) {
                String schoolId = rows.getString(1);
                String stored = rows.getString(2);
                String password = switch (schoolId) {
                    case TEST_STUDENT_ID -> TEST_STUDENT_PASSWORD;
                    case TEST_ADMIN_ID -> TEST_ADMIN_PASSWORD;
                    default -> DEMO_PASSWORD;
                };
                assertTrue(PasswordHasher.verify(password.toCharArray(), stored), schoolId);
                assertFalse(PasswordHasher.verify("not-the-password".toCharArray(), stored), schoolId);
                checked++;
            }
        }
        assertEquals(14, checked);
    }

    @Test
    void seededPdfFilesExistAtTheirIndexedPaths() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);

        int checked = 0;
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rows = s.executeQuery("SELECT file_path FROM digital_resources")) {
            while (rows.next()) {
                assertTrue(Files.isRegularFile(Path.of(rows.getString(1))), rows.getString(1));
                checked++;
            }
        }
        assertEquals(12, checked);
    }

    @Test
    void foreignKeysAreEnforcedOnEveryConnection() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);

        // Item 9999 does not exist.
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO loans (borrower_id, item_id, borrow_date, due_date, loan_period_days, status)
                VALUES (3, 9999, '2026-10-14', '2026-10-21', 7, 'Active')
                """));
    }

    @Test
    void loanPeriodOutsideThePolicyIsRejectedByTheDatabase() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO loans (borrower_id, item_id, borrow_date, due_date, loan_period_days, status)
                VALUES (3, 3, '2026-10-14', '2026-10-19', 5, 'Active')
                """));
    }

    @Test
    void anItemCannotHaveTwoOpenLoans() throws Exception {
        DatabaseInitializer.initialize(dbFile, true);

        // Item 1 already has the open loan 1 from the seed.
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO loans (borrower_id, item_id, borrow_date, due_date, loan_period_days, status)
                VALUES (3, 1, '2026-10-14', '2026-10-21', 7, 'Active')
                """));
    }

    @Test
    void splitStatementsDropsCommentsAndEmptyPieces() {
        List<String> statements = DatabaseInitializer.splitStatements("""
                -- a comment line
                CREATE TABLE a (x INTEGER);

                INSERT INTO a VALUES (1);
                """);
        assertEquals(List.of("CREATE TABLE a (x INTEGER)", "INSERT INTO a VALUES (1)"), statements);
    }

    private Connection open() throws SQLException {
        return new DatabaseManager(dbFile).openConnection();
    }

    private Set<String> tableNames() throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rows = s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rows = s.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
