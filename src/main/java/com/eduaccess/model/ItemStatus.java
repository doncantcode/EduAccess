package com.eduaccess.model;

/** physical_items.status. REJECTED is added to the spec so a rejected donation keeps a record. */
public enum ItemStatus implements DbCoded {
    PENDING("Pending"),
    AVAILABLE("Available"),
    BORROWED("Borrowed"),
    OVERDUE("Overdue"),
    RETURNED("Returned"),
    REJECTED("Rejected");

    private final String dbValue;

    ItemStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
