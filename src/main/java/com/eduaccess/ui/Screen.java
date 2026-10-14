package com.eduaccess.ui;

/** Every screen the shell can show. The label is the text on the left navigation button. */
enum Screen {
    DASHBOARD("Dashboard"),
    CATALOG("Physical Hub"),
    DONATE("Donate an Item"),
    MY_LOANS("My Loans"),
    LIBRARY("Digital Library"),
    AI("AI Study Assistant"),
    INVENTORY("Inventory & Approval"),
    IMPACT("Impact Dashboard");

    private final String label;

    Screen(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
