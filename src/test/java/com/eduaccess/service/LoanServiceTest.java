package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.dao.LoanDAO;
import com.eduaccess.dao.PhysicalItemDAO;
import com.eduaccess.dao.UserDAO;
import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.Intention;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.User;
import com.eduaccess.service.AuthService.RegistrationForm;
import com.eduaccess.service.InventoryService.DonationForm;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoanServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 1);

    @TempDir
    Path tempDir;

    private AuthService auth;
    private InventoryService inventory;
    private LoanService loans;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("eduaccess.db"));
        DatabaseInitializer.initialize(tempDir.resolve("eduaccess.db"), false);
        auth = new AuthService(db, new UserDAO());
        inventory = new InventoryService(db, new PhysicalItemDAO());
        loans = new LoanService(db, new LoanDAO(), new PhysicalItemDAO(), new LoanRules(LoanPolicy.DEFAULT));
    }

    @Test
    void borrowMarksTheItemBorrowedAndSetsTheDueDate() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);

        Loan loan = loans.borrow(student, item, 7, MONDAY);

        assertEquals(MONDAY.plusDays(7), loan.dueDate());
        assertEquals(LoanStatus.ACTIVE, loan.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(loan.feeAmount()));
        assertEquals(ItemStatus.BORROWED, inventory.find(item).status());
    }

    @Test
    void aStudentCanHoldAtMostThreeLoans() {
        User student = student("2026-SHS-0001");
        for (int i = 0; i < LoanPolicy.DEFAULT_MAX_OPEN_LOANS; i++) {
            loans.borrow(student, availableItem(student), 7, MONDAY);
        }
        long fourth = availableItem(student);

        assertThrows(ServiceException.class, () -> loans.borrow(student, fourth, 7, MONDAY));
        assertEquals(ItemStatus.AVAILABLE, inventory.find(fourth).status());
    }

    @Test
    void returningALoanFreesAPlaceUnderTheLimit() {
        User student = student("2026-SHS-0001");
        long first = loans.borrow(student, availableItem(student), 7, MONDAY).id();
        loans.borrow(student, availableItem(student), 7, MONDAY);
        loans.borrow(student, availableItem(student), 7, MONDAY);
        loans.returnItem(first, MONDAY.plusDays(1));

        loans.borrow(student, availableItem(student), 7, MONDAY.plusDays(1));
        assertEquals(4, loans.history(student, MONDAY.plusDays(1)).size());
    }

    @Test
    void anItemCannotBeBorrowedTwice() {
        User first = student("2026-SHS-0001");
        User second = student("2026-SHS-0002");
        long item = availableItem(first);
        loans.borrow(first, item, 3, MONDAY);

        assertThrows(ServiceException.class, () -> loans.borrow(second, item, 3, MONDAY));
    }

    @Test
    void borrowRefusesPeriodsOutsideThePolicy() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);

        assertThrows(ServiceException.class, () -> loans.borrow(student, item, 5, MONDAY));
        assertEquals(ItemStatus.AVAILABLE, inventory.find(item).status());
    }

    @Test
    void lateReturnChargesFivePesosPerDayThenCheckInMakesTheItemAvailable() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);
        Loan loan = loans.borrow(student, item, 3, MONDAY);

        Loan returned = loans.returnItem(loan.id(), MONDAY.plusDays(6));

        assertEquals(LoanStatus.RETURNED, returned.status());
        assertEquals(new BigDecimal("15.00"), returned.feeAmount());
        assertEquals(ItemStatus.RETURNED, inventory.find(item).status());

        inventory.checkIn(item);
        assertEquals(ItemStatus.AVAILABLE, inventory.find(item).status());
        assertThrows(ServiceException.class, () -> inventory.checkIn(item));
    }

    @Test
    void extendAddsThreeDaysOnceWhileOnTime() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);
        Loan loan = loans.borrow(student, item, 7, MONDAY);
        LocalDate due = loan.dueDate();

        assertEquals(due.plusDays(3), loans.extend(loan.id(), due));
        assertThrows(ServiceException.class, () -> loans.extend(loan.id(), due));
    }

    @Test
    void overdueLoansCannotBeExtended() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);
        Loan loan = loans.borrow(student, item, 3, MONDAY);

        ServiceException refused = assertThrows(ServiceException.class,
                () -> loans.extend(loan.id(), loan.dueDate().plusDays(1)));
        assertTrue(refused.getMessage().contains("overdue"));
    }

    @Test
    void refreshMarksLoanAndItemOverdueAndAccruesTheFee() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);
        Loan loan = loans.borrow(student, item, 7, MONDAY);
        LocalDate today = loan.dueDate().plusDays(4);

        List<LoanDetail> overdue = loans.overdueLoans(today);

        assertEquals(1, overdue.size());
        assertEquals(LoanStatus.OVERDUE, overdue.get(0).loan().status());
        assertEquals(new BigDecimal("20.00"), overdue.get(0).loan().feeAmount());
        assertEquals(ItemStatus.OVERDUE, inventory.find(item).status());
    }

    @Test
    void waivingTheFeeZeroesItAndRemovingTheWaiverRecalculatesIt() {
        User student = student("2026-SHS-0001");
        long item = availableItem(student);
        Loan loan = loans.borrow(student, item, 7, MONDAY);
        LocalDate today = loan.dueDate().plusDays(4);
        loans.overdueLoans(today);

        loans.setFeeWaived(loan.id(), true, today);
        LoanDetail waived = loans.overdueLoans(today).get(0);
        assertTrue(waived.loan().feeWaived());
        assertEquals(0, BigDecimal.ZERO.compareTo(waived.loan().feeAmount()));

        loans.setFeeWaived(loan.id(), false, today);
        LoanDetail restored = loans.overdueLoans(today).get(0);
        assertFalse(restored.loan().feeWaived());
        assertEquals(new BigDecimal("20.00"), restored.loan().feeAmount());
    }

    @Test
    void myLoansListsOnlyOpenLoansOfThatBorrower() {
        User alice = student("2026-SHS-0001");
        User bob = student("2026-SHS-0002");
        long calculator = availableItem(alice);
        long laptop = availableItem(bob);
        loans.borrow(alice, calculator, 7, MONDAY);
        loans.borrow(bob, laptop, 7, MONDAY);

        List<LoanDetail> aliceLoans = loans.myLoans(alice, MONDAY);

        assertEquals(1, aliceLoans.size());
        assertEquals(alice.schoolId(), aliceLoans.get(0).borrowerSchoolId());
    }

    private User student(String schoolId) {
        return auth.register(new RegistrationForm(schoolId, "Test Student", "Password1!".toCharArray(),
                EducationLevel.SHS, "STEM", 11, "Main Campus", Intention.BORROWER));
    }

    /** Donates an item and approves it, so it is on the shelf. */
    private long availableItem(User donor) {
        long id = inventory.submitDonation(donor, new DonationForm(
                "Scientific Calculator", "Casio fx-991EX", ItemCategory.CALCULATORS, ItemCondition.GOOD,
                "Library Counter A", List.of(EducationLevel.SHS), GivingType.DONATE, null));
        inventory.approve(id);
        return id;
    }
}
