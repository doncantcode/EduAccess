package com.eduaccess.ui;

import java.util.Map;

/**
 * The English/Filipino toggle in the header. It translates the navigation, the header, and every screen's
 * headings, section titles, subtitles and metric labels (Ui.heading, Ui.section, Ui.pageHeader, Ui.metric and
 * Ui.muted look text up here). Buttons, form labels and messages are still English. The Filipino wording should be
 * checked by a native speaker before release.
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
            Map.entry("Supporting SDG 4: Quality Education", "Para sa SDG 4: De-kalidad na Edukasyon"),
            // Screen headings, section titles, subtitles and metric labels (through the Ui helpers).
            Map.entry("1. Select your education level", "1. Piliin ang iyong antas ng edukasyon"),
            Map.entry("2. Program details", "2. Detalye ng programa"),
            Map.entry("3. Age and guardian consent", "3. Edad at pahintulot ng magulang o tagapag-alaga"),
            Map.entry("3. How will you use EduAccess?", "3. Paano mo gagamitin ang EduAccess?"),
            Map.entry("Active loans", "Aktibong hiram"),
            Map.entry("Active requests", "Mga aktibong hiling"),
            Map.entry("Active users", "Aktibong user"),
            Map.entry("Available for your level", "Available para sa iyong antas"),
            Map.entry("Borrow this item", "Hiramin ang gamit na ito"),
            Map.entry("Borrowed now", "Hiram ngayon"),
            Map.entry("Borrowed", "Hiniram"),
            Map.entry("Campus announcements", "Mga anunsyo sa kampus"),
            Map.entry("Completed", "Natapos"),
            Map.entry("Copy to USB drive", "Kopyahin sa USB drive"),
            Map.entry("Create your account", "Gumawa ng iyong account"),
            Map.entry("Digital downloads", "Mga digital na download"),
            Map.entry("Due soon", "Malapit nang ibalik"),
            Map.entry("Due within 2 days", "Ibabalik sa loob ng 2 araw"),
            Map.entry("Export reports", "I-export ang mga ulat"),
            Map.entry("Fees to settle", "Bayaring dapat ayusin"),
            Map.entry("Filled, waiting for the event", "Kumpleto na, hinihintay ang event"),
            Map.entry("Filters", "Mga filter"),
            Map.entry("Inventory & Donation Approval", "Imbentaryo at Pag-apruba ng Donasyon"),
            Map.entry("Item status tracker", "Katayuan ng gamit"),
            Map.entry("Items donated", "Mga gamit na naibigay"),
            Map.entry("Library Dashboard", "Dashboard ng Aklatan"),
            Map.entry("Live listing preview", "Silip sa listing"),
            Map.entry("Modules downloaded", "Mga module na na-download"),
            Map.entry("Most requested subjects", "Pinakahinihinging asignatura"),
            Map.entry("No active requests", "Walang aktibong hiling"),
            Map.entry("No matches", "Walang tugma"),
            Map.entry("Nothing matches these filters", "Walang tumugma sa mga filter"),
            Map.entry("Offers to review", "Mga alok na susuriin"),
            Map.entry("Offers", "Mga alok"),
            Map.entry("Offline storage", "Offline na storage"),
            Map.entry("Open requests", "Bukas na hiling"),
            Map.entry("Overdue items", "Mga gamit na lampas sa takdang araw"),
            Map.entry("Overdue", "Lampas na"),
            Map.entry("Past requests", "Mga nakaraang hiling"),
            Map.entry("Physical loans", "Mga pisikal na hiram"),
            Map.entry("Quick actions", "Mabilisang aksyon"),
            Map.entry("Recent activity", "Kamakailang aktibidad"),
            Map.entry("Resource use by education level", "Paggamit ayon sa antas ng edukasyon"),
            Map.entry("The library needs your help", "Kailangan ng aklatan ang tulong mo"),
            Map.entry("Total items", "Kabuuang gamit"),
            Map.entry("Waiting for approval", "Naghihintay ng pag-apruba"),
            Map.entry("What happens next", "Ano ang susunod"),
            Map.entry("Where it will appear", "Saan ito lalabas"),
            Map.entry("Your offers", "Ang iyong mga alok"),
            Map.entry("Campus Impact Dashboard", "Dashboard ng Epekto sa Kampus"),
            Map.entry("Search results", "Mga resulta ng paghahanap"),
            Map.entry("Borrow calculators, laptops, tablets and lab kits shared by the community.", "Humiram ng calculator, laptop, tablet at lab kit na ibinahagi ng komunidad."),
            Map.entry("Give a book or study tool to the library for good. Staff check every item before it is listed.", "Magbigay ng aklat o gamit pang-aral sa aklatan. Sinusuri ng staff ang bawat gamit bago ito ilista."),
            Map.entry("Physical loans, digital downloads and rentals across every education level.", "Mga pisikal na hiram, digital na download at upa sa bawat antas ng edukasyon."),
            Map.entry("Post what the library needs to rent for an event, then review what students offer.", "Ilagay ang kailangang upahan ng aklatan para sa isang event, saka suriin ang alok ng mga estudyante."),
            Map.entry("The library needs these items for a short time. Offer yours and get it back after the event.", "Kailangan ng aklatan ang mga gamit na ito nang sandali. Ialok ang sa iyo at makukuha mo ito pagkatapos ng event."),
            Map.entry("Textbooks, learner modules and reading material you can borrow from the shelves.", "Mga textbook, learner module at babasahin na mahihiram mo sa mga istante."),
            Map.entry("Track what you borrowed, when it is due and any fees.", "Subaybayan ang hiniram mo, kailan ito ibabalik at anumang bayarin."),
            Map.entry("Here is what is happening with your learning resources today.", "Narito ang nangyayari sa iyong mga learning resource ngayon."),
            Map.entry("Stock, borrowed items and approvals at a glance.", "Mabilisang tingin sa stock, hiniram na gamit at mga aaprubahan."),
            Map.entry("Students see these on their dashboard until the end date.", "Makikita ito ng mga estudyante sa kanilang dashboard hanggang sa huling araw."),
            Map.entry("No announcements right now.", "Walang anunsyo sa ngayon."),
            Map.entry("Books and equipment that match your education level.",
                    "Mga aklat at kagamitang angkop sa iyong antas ng edukasyon."));

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
