package com.eduaccess.model;

/** physical_items.type_of_giving: Donate transfers ownership, Lend keeps the donor as owner. */
public enum GivingType implements DbCoded {
    DONATE("Donate"),
    LEND("Lend");

    private final String dbValue;

    GivingType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
