package com.eduaccess.service;

import com.eduaccess.dao.MarketplaceDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.MarketplaceCategory;
import com.eduaccess.model.NewUser;
import com.eduaccess.model.PeerBorrow;
import com.eduaccess.model.PeerBorrowStatus;
import com.eduaccess.model.PeerMessage;
import com.eduaccess.model.Role;
import com.eduaccess.model.StudentListing;
import com.eduaccess.model.User;
import com.eduaccess.security.PasswordHasher;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceServiceTest {

    private DatabaseManager db;
    private MarketplaceService service;
    private UserDAO userDAO;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        DatabaseInitializer.initialize(dbFile, false);
        db = new DatabaseManager(dbFile);
        userDAO = new UserDAO();
        service = new MarketplaceService(db, new MarketplaceDAO(), userDAO);
    }

    private User registerUser(String schoolId, Role role, EducationLevel level, int year) {
        NewUser nu = new NewUser(schoolId, PasswordHasher.hash("password123".toCharArray()),
                "Test " + schoolId, role, Intention.BORROWER, level, "BS", year, "Main Campus");
        long id = Sql.write(db, c -> userDAO.registerUser(c, nu));
        return Sql.read(db, c -> userDAO.getProfile(c, id)).orElseThrow();
    }

    @Test
    void studentCanPostAndBrowseListings() {
        User student = registerUser("COL-101", Role.STUDENT, EducationLevel.COLLEGE, 2);
        MarketplaceService.ListingForm form = new MarketplaceService.ListingForm(
                "TI-84 Graphing Calculator", "Excellent condition, includes batteries.",
                MarketplaceCategory.CALCULATORS, ItemCondition.LIKE_NEW, EducationLevel.COLLEGE,
                BigDecimal.valueOf(10), "Student Center", null
        );
        long listingId = service.postListing(student, form);
        assertTrue(listingId > 0);

        List<StudentListing> browse = service.browse(null, null, null);
        assertEquals(1, browse.size());
        assertEquals("TI-84 Graphing Calculator", browse.get(0).title());
    }

    @Test
    void minorOrElementaryStudentCannotPost() {
        User elStudent = registerUser("EL-101", Role.STUDENT, EducationLevel.ELEMENTARY, 5);
        MarketplaceService.ListingForm form = new MarketplaceService.ListingForm(
                "Math Book", "Grade 5", MarketplaceCategory.TEXTBOOKS, ItemCondition.GOOD,
                EducationLevel.ELEMENTARY, BigDecimal.ZERO, "Library", null
        );
        Exception ex = assertThrows(ServiceException.class, () -> service.postListing(elStudent, form));
        assertTrue(ex.getMessage().contains("restricted") || ex.getMessage().contains("18 years"));
    }

    @Test
    void borrowRequestAndChatLifecycle() {
        User owner = registerUser("COL-201", Role.STUDENT, EducationLevel.COLLEGE, 3);
        User borrower = registerUser("COL-202", Role.STUDENT, EducationLevel.COLLEGE, 1);

        MarketplaceService.ListingForm form = new MarketplaceService.ListingForm(
                "Engineering Drafting Set", "Complete set with compass.",
                MarketplaceCategory.LAB_KITS, ItemCondition.GOOD, EducationLevel.COLLEGE,
                BigDecimal.ZERO, "Admin Office", null
        );
        long listingId = service.postListing(owner, form);

        // Send message
        long msgId = service.sendMessage(borrower, listingId, "Is this still available?");
        assertTrue(msgId > 0);

        List<PeerMessage> thread = service.thread(listingId, borrower);
        assertEquals(1, thread.size());
        assertEquals("Is this still available?", thread.get(0).body());

        // Request borrow
        long borrowId = service.requestBorrow(borrower, listingId, "Needed for drawing class.");
        assertTrue(borrowId > 0);

        List<PeerBorrow> incoming = service.incomingRequests(owner);
        assertEquals(1, incoming.size());

        // Accept borrow
        service.acceptBorrow(owner, borrowId);
        PeerBorrow updated = Sql.read(db, c -> new MarketplaceDAO().findBorrow(c, borrowId)).orElseThrow();
        assertEquals(PeerBorrowStatus.ACCEPTED, updated.status());
    }
}
