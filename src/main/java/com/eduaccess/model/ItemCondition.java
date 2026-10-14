package com.eduaccess.model;

/** physical_items.condition */
public enum ItemCondition implements DbCoded {
    LIKE_NEW("Like New"),
    GOOD("Good"),
    FAIR("Fair");

    private final String dbValue;

    ItemCondition(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
