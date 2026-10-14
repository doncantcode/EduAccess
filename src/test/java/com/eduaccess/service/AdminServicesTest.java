package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.DigitalResourceDAO;
import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.DigitalResource;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.ResourceTab;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.DigitalLibraryService.ResourceForm;
import com.eduaccess.service.InventoryService.DonationForm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Admin actions (add items, add PDFs, counter borrows) and the two-role sign-in rules, on a seeded database. */
class AdminServicesTest {

    private static final String DEMO_PASSWORD = "Demo#2026";

    @TempDir
    Path tempDir;

    private AuthService auth;
    private InventoryService inventory;
    private DigitalLibraryService library;
    private LoanService loans;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("eduaccess.db");
        DatabaseInitializer.initialize(dbFile, true);
        DatabaseManager db = new DatabaseManager(dbFile);
        auth = new AuthService(db, new UserDAO());
        inventory = new InventoryService(db, new PhysicalItemDAO());
        library = new DigitalLibraryService(db, new DigitalResourceDAO(), tempDir.resolve("library"));
        loans = new LoanService(db, new LoanDAO(), new PhysicalItemDAO(), new LoanRules(LoanPolicy.DEFAULT));
    }

    @Test
    void theTestAccountsSignInWithTheirPasswords() {
        User student = auth.login("test-student", "123".toCharArray(), LoginRole.STUDENT);
        User admin = auth.login("123", "admin".toCharArray(), LoginRole.LIBRARY_ADMIN);

        assertEquals(Role.STUDENT, student.role());
        assertEquals(Role.ADMIN, admin.role());
    }

    @Test
    void eachAccountOnlySignsInWithItsOwnRole() {
        assertThrows(ServiceException.class,
                () -> auth.login("2026-SHS-0001", DEMO_PASSWORD.toCharArray(), LoginRole.LIBRARY_ADMIN));
        assertThrows(ServiceException.class,
                () -> auth.login("LIB-0001", DEMO_PASSWORD.toCharArray(), LoginRole.STUDENT));
    }

    @Test
    void adminAddsAnAvailableLibraryItem() {
        long id = inventory.registerLibraryItem(admin(), donation("Protractor Set", ItemCategory.LAB_STUDY_KITS));

        PhysicalItem item = inventory.find(id);
        assertEquals(ItemStatus.AVAILABLE, item.status());
        assertNull(item.donorId());
    }

    @Test
    void studentCannotAddLibraryItems() {
        assertThrows(ServiceException.class,
                () -> inventory.registerLibraryItem(student(), donation("Protractor Set", ItemCategory.LAB_STUDY_KITS)));
    }

    @Test
    void adminAddsADigitalResourceAndTheFileIsCopiedIntoTheLibrary() throws Exception {
        Path pdf = Files.writeString(tempDir.resolve("grade8-cells.pdf"), "%PDF-1.4 sample");

        DigitalResource added = library.addResource(admin(), new ResourceForm("Grade 8 Science: Cells", "Science",
                ResourceTab.K12_MODULE_HUB, EducationLevel.JHS, "Test source", "CC BY 4.0", pdf));

        assertTrue(Files.isRegularFile(Path.of(added.filePath())));
        assertTrue(library.browse(ResourceTab.K12_MODULE_HUB, EducationLevel.JHS, "Cells").stream()
                .anyMatch(resource -> resource.id() == added.id()));
    }

    @Test
    void aCollegeResourceCannotGoInTheK12Hub() throws Exception {
        Path pdf = Files.writeString(tempDir.resolve("college.pdf"), "x");

        assertThrows(ServiceException.class, () -> library.addResource(admin(), new ResourceForm("Title", "Math",
                ResourceTab.K12_MODULE_HUB, EducationLevel.COLLEGE, "Source", "License", pdf)));
    }

    @Test
    void studentCannotAddDigitalResources() throws Exception {
        Path pdf = Files.writeString(tempDir.resolve("student.pdf"), "x");

        assertThrows(ServiceException.class, () -> library.addResource(student(), new ResourceForm("Title", "Math",
                ResourceTab.K12_MODULE_HUB, EducationLevel.JHS, "Source", "License", pdf)));
    }

    @Test
    void counterBorrowIsRecordedForAStudentOnly() {
        User borrower = auth.findStudent("2026-SHS-0001");
        assertThrows(ServiceException.class, () -> auth.findStudent("LIB-0001"));
        assertThrows(ServiceException.class, () -> auth.findStudent("123"));

        // Item 8 is available in the seed data.
        Loan loan = loans.borrow(borrower, 8, 7, LocalDate.of(2026, 10, 14));

        assertEquals(LoanStatus.ACTIVE, loan.status());
        assertEquals(LocalDate.of(2026, 10, 21), loan.dueDate());
    }

    private User admin() {
        return auth.login("LIB-0001", DEMO_PASSWORD.toCharArray(), LoginRole.LIBRARY_ADMIN);
    }

    private User student() {
        return auth.login("2026-SHS-0001", DEMO_PASSWORD.toCharArray(), LoginRole.STUDENT);
    }

    private static DonationForm donation(String title, ItemCategory category) {
        return new DonationForm(title, null, category, ItemCondition.GOOD, "Library Counter A",
                List.of(EducationLevel.JHS), GivingType.DONATE, null);
    }
}
