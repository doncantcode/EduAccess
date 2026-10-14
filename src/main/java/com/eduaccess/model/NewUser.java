package com.eduaccess.model;

/** Values for a new account. The password is already hashed. */
public record NewUser(
        String schoolId,
        String passwordHash,
        String fullName,
        Role role,
        Intention intention,
        EducationLevel educationLevel,
        String program,
        Integer yearLevel,
        String campus) {
}
