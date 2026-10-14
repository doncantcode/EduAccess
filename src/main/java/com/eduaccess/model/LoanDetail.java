package com.eduaccess.model;

/** A loan joined with the item title and the borrower, for display. */
public record LoanDetail(Loan loan, String itemTitle, String borrowerName, String borrowerSchoolId) {
}
