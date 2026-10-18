package com.eduaccess.model;

/** rental_requests.status. See schema.sql for the lifecycle. */
public enum RentalRequestStatus implements DbCoded {
    OPEN("Open"),
    FILLED("Filled"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired");

    private final String dbValue;

    RentalRequestStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
