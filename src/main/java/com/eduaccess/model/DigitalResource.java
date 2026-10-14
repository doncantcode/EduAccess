package com.eduaccess.model;

/** One PDF in the local digital library index. filePath is relative to the application folder. */
public record DigitalResource(
        long id,
        String title,
        ResourceTab tab,
        String subject,
        EducationLevel level,
        String filePath,
        double fileSizeMb,
        boolean cachedOffline,
        String source,
        String license) {
}
