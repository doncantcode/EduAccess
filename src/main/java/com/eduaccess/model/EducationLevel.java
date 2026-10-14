package com.eduaccess.model;

/** Academic level, used by users, physical_items and digital_resources. */
public enum EducationLevel implements DbCoded {
    ELEMENTARY("Elementary"),
    JHS("JHS"),
    SHS("SHS"),
    COLLEGE("College");

    private final String dbValue;

    EducationLevel(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
