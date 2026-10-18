package com.eduaccess.ui;

import java.util.Map;

/**
 * The English/Filipino toggle in the header. It translates the navigation and the header, which every screen
 * shares. Screen content is still English. Filipino wording should be checked by a native speaker before release.
 */
enum Lang {
    EN,
    FIL;

    private static Lang current = EN;

    private static final Map<String, String> FILIPINO = Map.ofEntries(
            Map.entry("Dashboard", "Dashboard"),
            Map.entry("Physical Library", "Pisikal na Aklatan"),
            Map.entry("Physical Hub", "Pisikal na Hub"),
            Map.entry("Library Rental Requests", "Mga Hiling na Upa ng Aklatan"),
            Map.entry("Donate an Item", "Mag-donate ng Gamit"),
            Map.entry("My Loans", "Aking mga Hiniram"),
            Map.entry("Digital Library", "Digital na Aklatan"),
            Map.entry("AI Study Assistant", "AI na Katuwang sa Pag-aaral"),
            Map.entry("Inventory & Approval", "Imbentaryo at Pag-apruba"),
            Map.entry("Impact Dashboard", "Dashboard ng Epekto"),
            Map.entry("MAIN MENU", "PANGUNAHING MENU"),
            Map.entry("ADMINISTRATION", "PANGANGASIWA"),
            Map.entry("Search items, e-books, modules...", "Maghanap ng gamit, e-book, module..."),
            Map.entry("Notifications", "Mga Abiso"),
            Map.entry("Sign out", "Mag-sign out"),
            Map.entry("Supporting SDG 4: Quality Education", "Para sa SDG 4: De-kalidad na Edukasyon"));

    static Lang current() {
        return current;
    }

    static void set(Lang lang) {
        current = lang;
    }

    /** The text in the current language. Text without a translation stays English. */
    static String t(String english) {
        return current == FIL ? FILIPINO.getOrDefault(english, english) : english;
    }
}
