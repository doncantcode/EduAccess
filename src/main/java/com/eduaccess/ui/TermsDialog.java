package com.eduaccess.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** Displays the EduAccess Terms of Use and Privacy Policy (RA 10173 compliance). */
public final class TermsDialog {

    private TermsDialog() {
    }

    public static void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Terms of Use & Privacy Policy");
        dialog.setHeaderText(null);
        Ui.style(dialog);

        VBox content = new VBox(14);
        content.setPadding(new Insets(20, 24, 20, 24));
        content.setPrefWidth(560);

        Label title = Ui.heading("EduAccess Terms of Use & Privacy Policy");
        Label subtitle = Ui.muted("Effective date: October 2026 | Republic Act 10173 (Data Privacy Act of 2012)");

        Label body = new Label("""
                1. Acceptance of Terms
                By registering for and using EduAccess, you agree to comply with and be bound by these Terms of Use and our Privacy Policy. If you do not agree to these terms, please do not use the application.

                2. Student Marketplace & Peer-to-Peer Lending
                - The Student Marketplace enables students 18 years of age or older to post items for lending, browse peer listings, and communicate directly via peer chat.
                - Library staff do not act as middlemen or intermediaries for marketplace transactions. All agreements, hand-overs, and returns are coordinated directly between participating students.
                - Stated daily fees are negotiated prices between students; EduAccess records these as stated terms but does not process payments or financial escrows.

                3. Library-Mediated Services
                - Loans of library-owned stock, donations, and Library Rental Requests are overseen by library administration.
                - Users must respect due dates and return policies set by the library.

                4. Data Privacy (RA 10173)
                - EduAccess collects only essential academic and profile information (School ID, full name, education level, program, year level, and campus) to provide educational materials and peer connectivity.
                - Learners under 18 years of age (Elementary, Junior High, and under-18 Senior High or College students) require verifiable parent or guardian consent to register. Guardian contact information is stored securely and never shared publicly.
                - Your data remains private and is never sold or used for commercial advertising.
                """);
        body.setWrapText(true);

        ScrollPane scroll = new ScrollPane(new VBox(12, title, subtitle, body));
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(420);

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
