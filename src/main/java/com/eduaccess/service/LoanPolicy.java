package com.eduaccess.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Loan terms the library can change without editing code.
 * {@link #DEFAULT} is the current policy: free loans, PHP 5.00 per day overdue, one 3-day extension.
 */
public record LoanPolicy(
        Set<Integer> loanPeriodDays,
        BigDecimal overdueFeePerDay,
        int extensionDays,
        int maxExtensions) {

    public static final LoanPolicy DEFAULT = new LoanPolicy(Set.of(3, 7, 14), new BigDecimal("5.00"), 3, 1);

    public LoanPolicy {
        loanPeriodDays = Set.copyOf(loanPeriodDays);
        if (loanPeriodDays.isEmpty() || loanPeriodDays.stream().anyMatch(days -> days <= 0)) {
            throw new IllegalArgumentException("Loan periods must be positive day counts");
        }
        if (overdueFeePerDay.signum() < 0) {
            throw new IllegalArgumentException("Overdue fee cannot be negative");
        }
        if (extensionDays <= 0 || maxExtensions < 0) {
            throw new IllegalArgumentException("Extension settings must be positive and non-negative");
        }
    }
}
