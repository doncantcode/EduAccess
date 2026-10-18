package com.eduaccess.model;

/** rental_requests.category: the kinds of things the library rents from students for events and workshops. */
public enum RentalCategory implements DbCoded {
    TECH("Tech"),
    EQUIPMENT("Equipment"),
    TOOLS("Tools"),
    DISPLAY("Display"),
    BOOKS_MEDIA("Books & Media"),
    OTHER("Other");

    private final String dbValue;

    RentalCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
