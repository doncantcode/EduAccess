package com.eduaccess.service;

import com.eduaccess.model.Role;
import com.eduaccess.model.User;

/** The two sign-in choices on the Login screen: a student or a library admin. */
public enum LoginRole {
    STUDENT("Student"),
    LIBRARY_ADMIN("Library Admin");

    private final String label;

    LoginRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean accepts(User user) {
        return switch (this) {
            case STUDENT -> user.role() == Role.STUDENT;
            case LIBRARY_ADMIN -> user.role() == Role.ADMIN;
        };
    }
}
