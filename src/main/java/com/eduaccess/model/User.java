package com.eduaccess.model;

/** A signed-in profile. The password hash never leaves the DAO layer. */
public record User(
        long id,
        String schoolId,
        String fullName,
        Role role,
        Intention intention,
        EducationLevel educationLevel,
        String program,
        Integer yearLevel,
        String campus) {
}
