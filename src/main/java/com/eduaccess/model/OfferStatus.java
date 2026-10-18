package com.eduaccess.model;

/** rental_offers.status. Offered, Approved, Checked-In, Returned, with Declined, Withdrawn and Cancelled as exits. */
public enum OfferStatus implements DbCoded {
    OFFERED("Offered"),
    APPROVED("Approved"),
    CHECKED_IN("Checked-In"),
    RETURNED("Returned"),
    DECLINED("Declined"),
    WITHDRAWN("Withdrawn"),
    CANCELLED("Cancelled");

    private final String dbValue;

    OfferStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
