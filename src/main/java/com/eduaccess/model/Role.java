package com.eduaccess.model;

/** users.role. EduAccess has two kinds of user: students and library admins. */
public enum Role implements DbCoded {
    STUDENT("Student"),
    ADMIN("Admin");

    private final String dbValue;

    Role(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String dbValue() {
        return dbValue;
    }
}
