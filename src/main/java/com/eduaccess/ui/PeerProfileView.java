package com.eduaccess.ui;

import com.eduaccess.model.StudentListing;
import com.eduaccess.model.User;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Displays a peer student's academic profile and active marketplace listings. */
public final class PeerProfileView {

    private final AppContext ctx;
    private final AppShell shell;
    private final long userId;

    public PeerProfileView(AppContext ctx, AppShell shell, long userId) {
        this.ctx = ctx;
        this.shell = shell;
        this.userId = userId;
    }

    public Node build() {
        User student = ctx.marketplace.getUserProfile(userId);

        VBox page = new VBox(20);
        page.setFillWidth(true);

        Button back = new Button("← Back");
        back.getStyleClass().add("button-secondary");
        back.setOnAction(event -> shell.go(Screen.MARKETPLACE));

        if (student == null) {
            page.getChildren().addAll(back, Ui.muted("Student profile not found."));
            return page;
        }

        Label name = Ui.heading(student.fullName());
        Label meta = Ui.muted(student.educationLevel().dbValue()
                + (student.program() != null ? " • " + student.program() : "")
                + (student.yearLevel() != null ? " • Year " + student.yearLevel() : "")
                + " • " + student.campus());

        Label privacyNotice = Ui.banner("Contact details are kept private. Coordinate hand-overs at designated pickup points.", "info");

        VBox profileCard = new VBox(10, name, meta, privacyNotice);
        profileCard.getStyleClass().add("card");
        page.getChildren().addAll(back, profileCard);

        // Active listings
        List<StudentListing> listings = ctx.marketplace.myListings(student);
        VBox listingsSection = new VBox(12, Ui.heading("Active Listings by " + student.fullName()));
        if (listings.isEmpty()) {
            listingsSection.getChildren().add(Ui.muted("This student has no marketplace listings currently."));
        } else {
            for (StudentListing listing : listings) {
                if (!"Active".equals(listing.status())) {
                    continue;
                }
                HBox row = new HBox(16);
                row.getStyleClass().add("card-flat");
                Label title = Ui.heading(listing.title());
                Label fee = new Label("₱" + listing.dailyFee().toPlainString() + "/day");
                Region r = new Region();
                HBox.setHgrow(r, Priority.ALWAYS);
                Button view = new Button("View");
                view.getStyleClass().add("button-secondary");
                view.setOnAction(event -> shell.show(new ListingDetailView(ctx, shell, listing.id()).build()));

                row.getChildren().addAll(Ui.column(4, title, fee), r, view);
                listingsSection.getChildren().add(row);
            }
        }
        page.getChildren().add(listingsSection);

        return page;
    }
}
