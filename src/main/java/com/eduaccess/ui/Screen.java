package com.eduaccess.ui;

/** Every screen the shell can show. The label is the English text on the left navigation button. */
enum Screen {
    DASHBOARD("Dashboard", Icons.HOME),
    PHYSICAL_LIBRARY("Physical Library", Icons.BOOK),
    CATALOG("Physical Hub", Icons.BOX),
    RENTAL_REQUESTS("Library Rental Requests", Icons.SWAP),
    MARKETPLACE("Student Marketplace", Icons.USERS),
    DONATE("Donate an Item", Icons.HEART),
    MY_LOANS("My Loans", Icons.LIST),
    LIBRARY("Digital Library", Icons.TABLET),
    AI("AI Study Assistant", Icons.SPARKLE),
    INVENTORY("Inventory & Approval", Icons.CLIPBOARD),
    PASSWORD_RESETS("Password Resets", Icons.USERS),
    IMPACT("Impact Dashboard", Icons.CHART);

    private final String label;
    private final Icons icon;

    Screen(String label, Icons icon) {
        this.label = label;
        this.icon = icon;
    }

    String label() {
        return label;
    }

    Icons icon() {
        return icon;
    }
}
