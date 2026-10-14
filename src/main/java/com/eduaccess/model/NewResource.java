package com.eduaccess.model;

/** Values for a digital resource that an admin has just copied into the library folder. */
public record NewResource(
        String title,
        ResourceTab tab,
        String subject,
        EducationLevel level,
        String filePath,
        double fileSizeMb,
        String source,
        String license) {
}
