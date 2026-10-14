package com.eduaccess.model;

/** loans.status */
public enum LoanStatus implements DbCoded {
    ACTIVE("Active"),
    OVERDUE("Overdue"),
    RETURNED("Returned");

    private final String dbValue;

    LoanStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
