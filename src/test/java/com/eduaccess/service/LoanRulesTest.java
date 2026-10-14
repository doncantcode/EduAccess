package com.eduaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eduaccess.model.LoanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoanRulesTest {

    private static final LocalDate BORROWED = LocalDate.of(2026, 10, 14);
    private static final LocalDate DUE = LocalDate.of(2026, 10, 21);

    private final LoanRules rules = new LoanRules(LoanPolicy.DEFAULT);

    @Test
    void dueDateIsBorrowDatePlusSelectedPeriod() {
        assertEquals(LocalDate.of(2026, 10, 17), rules.dueDate(BORROWED, 3));
        assertEquals(LocalDate.of(2026, 10, 21), rules.dueDate(BORROWED, 7));
        assertEquals(LocalDate.of(2026, 10, 28), rules.dueDate(BORROWED, 14));
    }

    @Test
    void rejectsPeriodsOutsideThePolicy() {
        assertThrows(IllegalArgumentException.class, () -> rules.dueDate(BORROWED, 5));
    }

    @Test
    void extensionAddsThreeDaysOnceAndOnlyOnTime() {
        assertTrue(rules.canExtend(DUE, DUE, 0));
        assertEquals(DUE.plusDays(3), rules.extendDueDate(DUE, DUE, 0));
        assertFalse(rules.canExtend(DUE, DUE, 1));
        assertThrows(IllegalStateException.class, () -> rules.extendDueDate(DUE, DUE, 1));
    }

    @Test
    void extensionIsRefusedOnceOverdue() {
        LocalDate oneDayLate = DUE.plusDays(1);
        assertFalse(rules.canExtend(oneDayLate, DUE, 0));
        assertThrows(IllegalStateException.class, () -> rules.extendDueDate(oneDayLate, DUE, 0));
    }

    @Test
    void noFeeOnOrBeforeTheDueDate() {
        assertEquals(0L, rules.daysOverdue(DUE, DUE));
        assertEquals(new BigDecimal("0.00"), rules.overdueFee(DUE, DUE));
        assertEquals(new BigDecimal("0.00"), rules.overdueFee(DUE, DUE.minusDays(2)));
    }

    @Test
    void overdueFeeIsFivePesosPerDay() {
        assertEquals(5L, rules.daysOverdue(DUE, DUE.plusDays(5)));
        assertEquals(new BigDecimal("25.00"), rules.overdueFee(DUE, DUE.plusDays(5)));
    }

    @Test
    void lateReturnIsChargedUpToTheReturnDate() {
        // Matches seeded loan 5: returned 3 days after the due date, fee 15.00.
        assertEquals(new BigDecimal("15.00"), rules.overdueFee(DUE, DUE.plusDays(3)));
    }

    @Test
    void equityWaiverMakesThePayableFeeZero() {
        assertEquals(new BigDecimal("0.00"), rules.payableFee(new BigDecimal("30.00"), true));
        assertEquals(new BigDecimal("30.00"), rules.payableFee(new BigDecimal("30.00"), false));
    }

    @Test
    void statusFollowsDueDateAndReturn() {
        assertEquals(LoanStatus.ACTIVE, rules.statusOn(DUE, DUE, false));
        assertEquals(LoanStatus.OVERDUE, rules.statusOn(DUE, DUE.plusDays(1), false));
        assertEquals(LoanStatus.RETURNED, rules.statusOn(DUE, DUE.plusDays(1), true));
    }

    @Test
    void policyChangesTakeEffectWithoutCodeChanges() {
        LoanPolicy strict = new LoanPolicy(Set.of(5), new BigDecimal("10.00"), 2, 2);
        LoanRules custom = new LoanRules(strict);

        assertEquals(BORROWED.plusDays(5), custom.dueDate(BORROWED, 5));
        assertThrows(IllegalArgumentException.class, () -> custom.dueDate(BORROWED, 7));
        assertTrue(custom.canExtend(DUE, DUE, 1));
        assertEquals(new BigDecimal("30.00"), custom.overdueFee(DUE, DUE.plusDays(3)));
    }

    @Test
    void invalidPolicyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoanPolicy(Set.of(), BigDecimal.ONE, 3, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new LoanPolicy(Set.of(7), new BigDecimal("-1.00"), 3, 1));
    }
}
