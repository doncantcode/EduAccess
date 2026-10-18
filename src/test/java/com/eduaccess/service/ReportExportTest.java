package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.DigitalResourceDAO;
import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.ReportDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.User;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every export report against the demo data, the level filter, and the full-database workbook. */
class ReportExportTest {

    @TempDir
    Path tempDir;

    private ReportService reports;
    private AuthService auth;

    @BeforeEach
    void setUp() throws Exception {
        Path file = tempDir.resolve("eduaccess.db");
        DatabaseInitializer.initialize(file, true);
        DatabaseManager db = new DatabaseManager(file);
        reports = new ReportService(db, new LoanDAO(), new PhysicalItemDAO(), new DigitalResourceDAO(),
                new ReportDAO());
        auth = new AuthService(db, new UserDAO());
    }

    @Test
    void everyReportHasHeadersAndRowsFromTheDemoData() {
        for (ReportType type : ReportType.values()) {
            Report report = reports.report(type, null);
            assertFalse(report.headers().isEmpty(), type.name());
            assertFalse(report.rows().isEmpty(), type + " should have demo rows");
            report.rows().forEach(row -> assertEquals(report.headers().size(), row.size(), type.name()));
        }
    }

    @Test
    void theLevelFilterKeepsOnlyThatLevel() {
        Report all = reports.report(ReportType.USERS, null);
        Report college = reports.report(ReportType.USERS, EducationLevel.COLLEGE);
        int level = college.headers().indexOf("Level");

        assertTrue(college.rows().size() < all.rows().size());
        college.rows().forEach(row -> assertEquals("College", row.get(level)));
        assertTrue(college.name().endsWith("College"));
    }

    @Test
    void passwordsAreNeverExported() {
        for (Report report : reports.fullDatabase()) {
            report.headers().forEach(header ->
                    assertFalse(header.toLowerCase(Locale.ROOT).contains("password"), report.name()));
        }
    }

    @Test
    void theFullDatabaseWorkbookHasACoverSheetAndOneSheetPerReport() throws Exception {
        Path target = tempDir.resolve("eduaccess-database.xlsx");
        List<Report> all = reports.fullDatabase();
        new FileExportService().writeWorkbook(all, "EduAccess database export", target);

        try (InputStream in = Files.newInputStream(target); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(all.size() + 1, workbook.getNumberOfSheets());
            assertEquals("About", workbook.getSheetName(0));
            Sheet loans = workbook.getSheet("Borrowing log");
            assertEquals("Loan ID", loans.getRow(0).getCell(0).getStringCellValue());
            assertEquals(all.get(0).rows().size(), loans.getLastRowNum());
        }
    }

    @Test
    void aStudentsHistoryHasOnlyTheirLoans() {
        User student = auth.login("2026-SHS-0001", "Demo#2026".toCharArray(), LoginRole.STUDENT);
        Report history = reports.loanHistory(student);
        Report everyone = reports.report(ReportType.BORROWING_LOG, null);

        assertFalse(history.rows().isEmpty());
        assertTrue(history.rows().size() < everyone.rows().size());
    }
}
