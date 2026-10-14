package com.eduaccess.model;

/** users.intention, chosen in the registration wizard. */
public enum Intention implements DbCoded {
    BORROWER("Borrower"),
    DONOR("Donor"),
    BOTH("Both");

    private final String dbValue;

    Intention(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
