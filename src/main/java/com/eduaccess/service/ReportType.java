package com.eduaccess.service;

/**
 * The tables staff can export to Excel or CSV. levelFilter says whether the education-level filter applies.
 * Every report includes every record. Passwords and guardian contact details are never exported.
 */
public enum ReportType {
    BORROWING_LOG("Borrowing log", true),
    OVERDUE_FEES("Overdue loans and fees", true),
    INVENTORY("Inventory", true),
    DONATIONS("Donations", true),
    DIGITAL_DOWNLOADS("Digital downloads", true),
    DIGITAL_LIBRARY("Digital library index", true),
    RENTAL_REQUESTS("Rental requests", false),
    RENTAL_OFFERS("Rental offers", true),
    USERS("Users", true),
    ANNOUNCEMENTS("Announcements", false);

    private final String label;
    private final boolean levelFilter;

    ReportType(String label, boolean levelFilter) {
        this.label = label;
        this.levelFilter = levelFilter;
    }

    public String label() {
        return label;
    }

    public boolean levelFilter() {
        return levelFilter;
    }
}
