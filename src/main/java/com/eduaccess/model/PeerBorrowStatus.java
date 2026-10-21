package com.eduaccess.model;

/** Status of a peer borrow request in the Student Marketplace. */
public enum PeerBorrowStatus implements DbCoded {
    REQUESTED("Requested"),
    ACCEPTED("Accepted"),
    BORROWED("Borrowed"),
    RETURNED("Returned"),
    DECLINED("Declined"),
    CANCELLED("Cancelled");

    private final String dbValue;

    PeerBorrowStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
