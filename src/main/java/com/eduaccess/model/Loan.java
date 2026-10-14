package com.eduaccess.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the loans table. returnDate is null while the loan is open. */
public record Loan(
        long id,
        long borrowerId,
        long itemId,
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnDate,
        int loanPeriodDays,
        int extensionCount,
        BigDecimal feeAmount,
        boolean feeWaived,
        LoanStatus status) {
}
