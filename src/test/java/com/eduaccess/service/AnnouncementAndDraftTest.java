package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.AnnouncementDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.DonationDraft;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.Intention;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.AuthService.RegistrationForm;
import com.eduaccess.service.InventoryService.DonationForm;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Campus announcements and saved donation drafts. */
class AnnouncementAndDraftTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    @TempDir
    Path tempDir;

    private AuthService auth;
    private AnnouncementService announcements;
    private InventoryService inventory;
    private User staff;
    private User student;

    @BeforeEach
    void setUp() throws Exception {
        Path file = tempDir.resolve("eduaccess.db");
        DatabaseInitializer.initialize(file, false);
        DatabaseManager db = new DatabaseManager(file);
        auth = new AuthService(db, new UserDAO());
        announcements = new AnnouncementService(db, new AnnouncementDAO());
        inventory = new InventoryService(db, new PhysicalItemDAO());
        student = auth.register(new RegistrationForm("2026-COL-0001", "Carlo Reyes", "Password1!".toCharArray(),
                EducationLevel.COLLEGE, "BS Computer Science", 1, "Main Campus", Intention.BOTH));
        User account = auth.register(new RegistrationForm("LIB-TEST", "Librarian", "Password1!".toCharArray(),
                EducationLevel.COLLEGE, "Staff", 1, "Main Campus", Intention.BOTH));
        staff = new User(account.id(), account.schoolId(), "Librarian", Role.ADMIN, null, null, null, null, null);
    }

    @Test
    void anAnnouncementShowsUntilItsEndDateThenDisappears() {
        announcements.post(staff, "Donation drive", "Drop off calculators at Counter A.", TODAY.plusDays(5), TODAY);

        assertEquals(1, announcements.visible(TODAY).size());
        assertEquals(1, announcements.visible(TODAY.plusDays(5)).size());
        assertTrue(announcements.visible(TODAY.plusDays(6)).isEmpty());
    }

    @Test
    void removedAnnouncementsAreHiddenButKept() {
        long id = announcements.post(staff, "Exam week hours", "Open until 8 PM.", null, TODAY);
        announcements.remove(staff, id);

        assertTrue(announcements.visible(TODAY).isEmpty());
        assertEquals(1, announcements.all(staff).size());
        assertThrows(ServiceException.class, () -> announcements.remove(staff, id));
    }

    @Test
    void onlyStaffPostAndEmptyOrPastAnnouncementsAreRefused() {
        assertThrows(ServiceException.class, () -> announcements.post(student, "Hi", "Text", null, TODAY));
        assertThrows(ServiceException.class, () -> announcements.post(staff, " ", "Text", null, TODAY));
        assertThrows(ServiceException.class, () -> announcements.post(staff, "Title", "Text", TODAY.minusDays(1),
                TODAY));
    }

    @Test
    void anIncompleteDonationCanBeSavedAndRestored() {
        inventory.saveDraft(student, new DonationForm("Casio fx-991EX", null, ItemCategory.CALCULATORS, null,
                "Library Counter B", List.of(EducationLevel.SHS), GivingType.DONATE, null));

        DonationDraft draft = inventory.draft(student).orElseThrow();
        assertEquals("Casio fx-991EX", draft.title());
        assertEquals(ItemCategory.CALCULATORS, draft.category());
        assertEquals(List.of(EducationLevel.SHS), draft.levels());
        assertEquals("Library Counter B", draft.dropoffPoint());

        inventory.discardDraft(student);
        assertTrue(inventory.draft(student).isEmpty());
    }

    @Test
    void submittingTheDonationClearsTheDraft() {
        DonationForm form = new DonationForm("Scientific Calculator", "Casio fx-991EX", ItemCategory.CALCULATORS,
                ItemCondition.GOOD, "Library Counter A", List.of(EducationLevel.SHS), GivingType.DONATE, null);
        inventory.saveDraft(student, form);

        inventory.submitDonation(student, form);

        assertTrue(inventory.draft(student).isEmpty());
    }
}
