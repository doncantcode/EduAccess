package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.RentalDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalCategory;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.RentalRequestStatus;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.AuthService.RegistrationForm;
import com.eduaccess.service.RentalService.OfferForm;
import com.eduaccess.service.RentalService.RequestForm;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Library Rental Requests lifecycle: request, offer, approve, check in, return. */
class RentalServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    @TempDir
    Path tempDir;

    private AuthService auth;
    private RentalService rentals;
    private User staff;

    @BeforeEach
    void setUp() throws Exception {
        Path file = tempDir.resolve("eduaccess.db");
        DatabaseInitializer.initialize(file, false);
        DatabaseManager db = new DatabaseManager(file);
        auth = new AuthService(db, new UserDAO());
        rentals = new RentalService(db, new RentalDAO());
        User account = student("LIB-TEST");
        staff = new User(account.id(), account.schoolId(), "Librarian", Role.ADMIN, null, null, null, null, null);
    }

    @Test
    void staffPostARequestThatStudentsSeeAsOpen() {
        long id = request(2, ItemCondition.FAIR);

        RentalRequest open = rentals.openRequests(TODAY).get(0);
        assertEquals(id, open.id());
        assertEquals(RentalRequestStatus.OPEN, open.status());
        assertEquals(4, open.rentalDays());
    }

    @Test
    void onlyStaffCanPostRequests() {
        User student = student("2026-SHS-0001");
        assertThrows(ServiceException.class, () -> rentals.createRequest(student, form(1, ItemCondition.FAIR), TODAY));
    }

    @Test
    void offersMustCloseBeforeTheRentalStarts() {
        RequestForm late = new RequestForm("Projector", "Any HDMI projector", "Science fair", RentalCategory.TECH, 1,
                ItemCondition.FAIR, TODAY.plusDays(9), TODAY.plusDays(6), TODAY.plusDays(8), BigDecimal.TEN,
                "Library Counter A");
        assertThrows(ServiceException.class, () -> rentals.createRequest(staff, late, TODAY));
    }

    @Test
    void approvingEnoughOffersFillsTheRequestAndDeclinesTheRest() {
        long id = request(1, ItemCondition.FAIR);
        long first = rentals.offer(student("2026-SHS-0001"), id, offer(ItemCondition.GOOD), TODAY);
        long second = rentals.offer(student("2026-SHS-0002"), id, offer(ItemCondition.GOOD), TODAY);

        rentals.approve(staff, first);

        assertEquals(RentalRequestStatus.FILLED, rentals.request(id).status());
        RentalOffer declined = offer(id, second);
        assertEquals(OfferStatus.DECLINED, declined.status());
        assertEquals(RentalService.REASON_FILLED, declined.declineReason());
        assertEquals(0, new BigDecimal("150.00").compareTo(offer(id, first).agreedFee()));
    }

    @Test
    void aStudentCannotOfferTwiceOnTheSameRequest() {
        long id = request(2, ItemCondition.FAIR);
        User owner = student("2026-SHS-0001");
        rentals.offer(owner, id, offer(ItemCondition.GOOD), TODAY);

        assertThrows(ServiceException.class, () -> rentals.offer(owner, id, offer(ItemCondition.GOOD), TODAY));
    }

    @Test
    void itemsBelowTheMinimumConditionAreRefused() {
        long id = request(1, ItemCondition.GOOD);
        assertThrows(ServiceException.class,
                () -> rentals.offer(student("2026-SHS-0001"), id, offer(ItemCondition.FAIR), TODAY));
    }

    @Test
    void theOwnerMustConfirmTheHandOver() {
        long id = request(1, ItemCondition.FAIR);
        OfferForm unconfirmed = new OfferForm("Projector", null, null, ItemCondition.GOOD, null, null, false, true);
        assertThrows(ServiceException.class, () -> rentals.offer(student("2026-SHS-0001"), id, unconfirmed, TODAY));
    }

    @Test
    void theLibraryMustSetWhatItPays() {
        RequestForm unpaid = new RequestForm("Projector", "Any HDMI projector", "Science fair", RentalCategory.TECH, 1,
                ItemCondition.FAIR, TODAY.plusDays(3), TODAY.plusDays(5), TODAY.plusDays(8), BigDecimal.ZERO,
                "Library Counter A");
        assertThrows(ServiceException.class, () -> rentals.createRequest(staff, unpaid, TODAY));
    }

    @Test
    void studentsMustConfirmTheyAre18OrOlder() {
        long id = request(1, ItemCondition.FAIR);
        OfferForm minor = new OfferForm("Projector", null, null, ItemCondition.GOOD, null, null, true, false);
        assertThrows(ServiceException.class, () -> rentals.offer(student("2026-SHS-0001"), id, minor, TODAY));
    }

    @Test
    void juniorHighAndElementaryStudentsCannotOffer() {
        long id = request(1, ItemCondition.FAIR);
        User juniorHigh = auth.register(new RegistrationForm("2026-JHS-0009", "Young Student",
                "Password1!".toCharArray(), EducationLevel.JHS, null, 9, "Main Campus", Intention.BOTH));

        assertFalse(RentalService.mayOffer(juniorHigh));
        assertThrows(ServiceException.class, () -> rentals.offer(juniorHigh, id, offer(ItemCondition.GOOD), TODAY));
    }

    @Test
    void checkInRequiresTheStaffIdCheck() {
        long id = request(1, ItemCondition.FAIR);
        long offerId = rentals.offer(student("2026-SHS-0001"), id, offer(ItemCondition.GOOD), TODAY);
        rentals.approve(staff, offerId);

        assertThrows(ServiceException.class, () -> rentals.checkIn(staff, offerId, ItemCondition.GOOD, false));
    }

    @Test
    void withdrawingAnApprovedOfferReopensAFilledRequest() {
        long id = request(1, ItemCondition.FAIR);
        User owner = student("2026-SHS-0001");
        long offerId = rentals.offer(owner, id, offer(ItemCondition.GOOD), TODAY);
        rentals.approve(staff, offerId);

        rentals.withdraw(owner, offerId, TODAY);

        assertEquals(OfferStatus.WITHDRAWN, offer(id, offerId).status());
        assertEquals(RentalRequestStatus.OPEN, rentals.request(id).status());
    }

    @Test
    void checkInThenReturnCompletesTheRequest() {
        long id = request(1, ItemCondition.FAIR);
        long offerId = rentals.offer(student("2026-SHS-0001"), id, offer(ItemCondition.GOOD), TODAY);
        rentals.approve(staff, offerId);

        assertThrows(ServiceException.class,
                () -> rentals.returnToOwner(staff, offerId, ItemCondition.GOOD, null, true, TODAY));
        rentals.checkIn(staff, offerId, ItemCondition.GOOD, true);
        assertThrows(ServiceException.class, () -> rentals.withdraw(student("2026-SHS-0002"), offerId, TODAY));
        rentals.returnToOwner(staff, offerId, ItemCondition.GOOD, null, true, TODAY.plusDays(10));

        RentalOffer returned = offer(id, offerId);
        assertEquals(OfferStatus.RETURNED, returned.status());
        assertTrue(returned.feePaid());
        assertEquals(RentalRequestStatus.COMPLETED, rentals.request(id).status());
    }

    @Test
    void afterOffersCloseARequestWithApprovedItemsIsFilledAndOneWithNothingExpires() {
        long withItems = request(3, ItemCondition.FAIR);
        long empty = request(1, ItemCondition.FAIR);
        long offerId = rentals.offer(student("2026-SHS-0001"), withItems, offer(ItemCondition.GOOD), TODAY);
        rentals.approve(staff, offerId);

        rentals.refreshDeadlines(TODAY.plusDays(4));

        assertEquals(RentalRequestStatus.FILLED, rentals.request(withItems).status());
        assertEquals(RentalRequestStatus.EXPIRED, rentals.request(empty).status());
        assertThrows(ServiceException.class,
                () -> rentals.offer(student("2026-SHS-0002"), withItems, offer(ItemCondition.GOOD), TODAY.plusDays(4)));
    }

    @Test
    void cancellingARequestDeclinesWaitingOffersAndCancelsApprovedOnes() {
        long id = request(2, ItemCondition.FAIR);
        long approved = rentals.offer(student("2026-SHS-0001"), id, offer(ItemCondition.GOOD), TODAY);
        long waiting = rentals.offer(student("2026-SHS-0002"), id, offer(ItemCondition.GOOD), TODAY);
        rentals.approve(staff, approved);

        rentals.cancelRequest(staff, id);

        assertEquals(RentalRequestStatus.CANCELLED, rentals.request(id).status());
        assertEquals(OfferStatus.CANCELLED, offer(id, approved).status());
        assertEquals(OfferStatus.DECLINED, offer(id, waiting).status());
    }

    @Test
    void aNoShowCancelsTheOfferAndReopensTheRequest() {
        long id = request(1, ItemCondition.FAIR);
        long offerId = rentals.offer(student("2026-SHS-0001"), id, offer(ItemCondition.GOOD), TODAY);
        rentals.approve(staff, offerId);

        rentals.markNoShow(staff, offerId, TODAY);

        assertEquals(OfferStatus.CANCELLED, offer(id, offerId).status());
        assertEquals(RentalRequestStatus.OPEN, rentals.request(id).status());
    }

    /** Offers close in 3 days, the library needs the item from day 5 to day 8, and pays ₱150 per item. */
    private long request(int quantity, ItemCondition minimum) {
        return rentals.createRequest(staff, form(quantity, minimum), TODAY);
    }

    private static RequestForm form(int quantity, ItemCondition minimum) {
        return new RequestForm("Portable projector", "HDMI input, 2000 lumens or brighter", "Science Fair 2026",
                RentalCategory.TECH, quantity, minimum, TODAY.plusDays(3), TODAY.plusDays(5), TODAY.plusDays(8),
                new BigDecimal("150"), "Library Counter A");
    }

    private static OfferForm offer(ItemCondition condition) {
        return new OfferForm("Epson projector", "Epson EB-X51", "With HDMI cable", condition, null, null, true, true);
    }

    private RentalOffer offer(long requestId, long offerId) {
        return rentals.offersFor(staff, requestId).stream().filter(o -> o.id() == offerId).findFirst().orElseThrow();
    }

    private User student(String schoolId) {
        return auth.register(new RegistrationForm(schoolId, "Test Student", "Password1!".toCharArray(),
                EducationLevel.SHS, "STEM", 11, "Main Campus", Intention.BOTH));
    }
}
