package com.eduaccess.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Loan terms the library can change without editing code.
 * {@link #DEFAULT} is the current policy: free loans, PHP 5.00 per day overdue, one 3-day extension, and at most
 * {@value #DEFAULT_MAX_OPEN_LOANS} items on loan per student at once.
 */
public record LoanPolicy(
        Set<Integer> loanPeriodDays,
        BigDecimal overdueFeePerDay,
        int extensionDays,
        int maxExtensions,
        int maxOpenLoans) {

    public static final int DEFAULT_MAX_OPEN_LOANS = 3;

    public static final LoanPolicy DEFAULT =
            new LoanPolicy(Set.of(3, 7, 14), new BigDecimal("5.00"), 3, 1, DEFAULT_MAX_OPEN_LOANS);

    /** A policy with the default limit of open loans per student. */
    public LoanPolicy(Set<Integer> loanPeriodDays, BigDecimal overdueFeePerDay, int extensionDays, int maxExtensions) {
        this(loanPeriodDays, overdueFeePerDay, extensionDays, maxExtensions, DEFAULT_MAX_OPEN_LOANS);
    }

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
        if (maxOpenLoans <= 0) {
            throw new IllegalArgumentException("The open-loan limit must be at least 1");
        }
    }
}
