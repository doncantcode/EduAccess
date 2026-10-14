package com.eduaccess.service;

import com.eduaccess.model.LoanStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Due dates, extensions, overdue fees and status for physical loans.
 * Pure functions over dates and amounts, with no database access.
 */
public final class LoanRules {

    private final LoanPolicy policy;

    public LoanRules(LoanPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public LoanPolicy policy() {
        return policy;
    }

    public LocalDate dueDate(LocalDate borrowDate, int loanPeriodDays) {
        if (!policy.loanPeriodDays().contains(loanPeriodDays)) {
            throw new IllegalArgumentException("Loan period not allowed: " + loanPeriodDays + " days");
        }
        return borrowDate.plusDays(loanPeriodDays);
    }

    /**
     * Extensions are allowed only while the loan is on time. Once overdue, extending would hide the fee
     * already owed. The caller must also make sure the loan has not been returned.
     */
    public boolean canExtend(LocalDate asOf, LocalDate dueDate, int extensionCount) {
        return !asOf.isAfter(dueDate) && extensionCount < policy.maxExtensions();
    }

    public LocalDate extendDueDate(LocalDate asOf, LocalDate dueDate, int extensionCount) {
        if (!canExtend(asOf, dueDate, extensionCount)) {
            throw new IllegalStateException("Loan cannot be extended: already overdue or extension limit reached");
        }
        return dueDate.plusDays(policy.extensionDays());
    }

    /** Days past the due date. Zero while the loan is on time. For a returned loan, pass the return date as asOf. */
    public long daysOverdue(LocalDate dueDate, LocalDate asOf) {
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, asOf));
    }

    public BigDecimal overdueFee(LocalDate dueDate, LocalDate asOf) {
        return policy.overdueFeePerDay()
                .multiply(BigDecimal.valueOf(daysOverdue(dueDate, asOf)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** The fee the borrower must settle. An equity-program waiver makes it zero. */
    public BigDecimal payableFee(BigDecimal accruedFee, boolean feeWaived) {
        return feeWaived ? BigDecimal.ZERO.setScale(2) : accruedFee.setScale(2, RoundingMode.HALF_UP);
    }

    public LoanStatus statusOn(LocalDate dueDate, LocalDate asOf, boolean returned) {
        if (returned) {
            return LoanStatus.RETURNED;
        }
        return asOf.isAfter(dueDate) ? LoanStatus.OVERDUE : LoanStatus.ACTIVE;
    }
}
