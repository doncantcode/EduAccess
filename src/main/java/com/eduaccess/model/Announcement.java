package com.eduaccess.model;

import java.time.LocalDate;

/** A campus announcement. expiresOn null means it stays until staff remove it. */
public record Announcement(
        long id,
        String title,
        String body,
        String postedBy,
        LocalDate postedOn,
        LocalDate expiresOn,
        boolean removed) {

    public boolean visibleOn(LocalDate day) {
        return !removed && !day.isBefore(postedOn) && (expiresOn == null || !day.isAfter(expiresOn));
    }
}
