package com.eduaccess.ui;

import com.eduaccess.model.PeerBorrow;
import com.eduaccess.model.PeerBorrowStatus;
import com.eduaccess.model.StudentListing;
import com.eduaccess.model.User;
import com.eduaccess.service.ServiceException;
import java.math.BigDecimal;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Detailed view of a marketplace listing, with borrow requests for owners or action buttons for borrowers. */
public final class ListingDetailView {

    private final AppContext ctx;
    private final AppShell shell;
    private final long listingId;

    public ListingDetailView(AppContext ctx, AppShell shell, long listingId) {
        this.ctx = ctx;
        this.shell = shell;
        this.listingId = listingId;
    }

    public Node build() {
        User user = ctx.session.require();
        StudentListing listing = ctx.marketplace.getListing(listingId);
        User owner = ctx.marketplace.getUserProfile(listing.ownerId());

        VBox page = new VBox(20);
        page.setFillWidth(true);

        Button back = new Button("← Back to Marketplace");
        back.getStyleClass().add("button-secondary");
        back.setOnAction(event -> shell.go(Screen.MARKETPLACE));

        Label title = Ui.heading(listing.title());
        Label category = Ui.muted("Category: " + listing.category().dbValue() + " • Level: " + listing.educationLevel().dbValue());
        Label condition = new Label("Condition: " + listing.condition().dbValue());
        condition.getStyleClass().add("badge-blue");

        Label fee = new Label(listing.dailyFee().compareTo(BigDecimal.ZERO) == 0 ? "Free to borrow"
                : "₱" + listing.dailyFee().toPlainString() + " / day");
        fee.getStyleClass().add("badge-teal");

        Label pickup = Ui.muted("Pickup point: " + listing.pickupPoint());
        Label desc = new Label(listing.description());
        desc.setWrapText(true);

        VBox detailsCard = new VBox(12, category, Ui.row(12, condition, fee), pickup, desc);
        detailsCard.getStyleClass().add("card");

        // Owner box
        HBox ownerBox = new HBox(12);
        ownerBox.getStyleClass().add("card-flat");
        ownerBox.setAlignment(Pos.CENTER_LEFT);
        Label ownerLbl = Ui.heading("Posted by " + (owner != null ? owner.fullName() : "Student"));
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        Button viewProfile = new Button("View student profile");
        viewProfile.getStyleClass().add("button-secondary");
        if (owner != null) {
            viewProfile.setOnAction(event -> shell.show(new PeerProfileView(ctx, shell, owner.id()).build()));
        }
        ownerBox.getChildren().addAll(ownerLbl, r, viewProfile);

        page.getChildren().addAll(back, title, detailsCard, ownerBox);

        // Actions
        if (owner != null && owner.id() == user.id()) {
            VBox ownerSection = new VBox(12, Ui.heading("Manage your listing & borrow requests"));
            List<PeerBorrow> borrows = ctx.marketplace.borrowsForListing(listingId);
            if (borrows.isEmpty()) {
                ownerSection.getChildren().add(Ui.muted("No student borrow requests yet."));
            } else {
                for (PeerBorrow borrow : borrows) {
                    User borrower = ctx.marketplace.getUserProfile(borrow.borrowerId());
                    HBox browRow = new HBox(12);
                    browRow.getStyleClass().add("card-flat");
                    browRow.setAlignment(Pos.CENTER_LEFT);

                    Label bName = Ui.heading(borrower != null ? borrower.fullName() : "Student");
                    Label bStatus = new Label(borrow.status().dbValue());
                    bStatus.getStyleClass().add("badge-amber");
                    Label bNote = Ui.muted("Note: " + (borrow.note() != null ? borrow.note() : "None"));

                    Region br = new Region();
                    HBox.setHgrow(br, Priority.ALWAYS);

                    browRow.getChildren().addAll(Ui.column(4, bName, bNote), bStatus, br);

                    if (borrow.status() == PeerBorrowStatus.REQUESTED) {
                        Button accept = new Button("Accept");
                        accept.getStyleClass().add("button-teal");
                        accept.setOnAction(event -> {
                            try {
                                ctx.marketplace.acceptBorrow(user, borrow.id());
                                shell.show(build());
                            } catch (ServiceException e) {
                                Ui.error(e.getMessage());
                            }
                        });
                        Button decline = new Button("Decline");
                        decline.getStyleClass().add("button-secondary");
                        decline.setOnAction(event -> {
                            try {
                                ctx.marketplace.declineBorrow(user, borrow.id());
                                shell.show(build());
                            } catch (ServiceException e) {
                                Ui.error(e.getMessage());
                            }
                        });
                        browRow.getChildren().addAll(accept, decline);
                    } else if (borrow.status() == PeerBorrowStatus.ACCEPTED) {
                        Button markBorrowed = new Button("Mark Handed Over");
                        markBorrowed.getStyleClass().add("button-teal");
                        markBorrowed.setOnAction(event -> {
                            try {
                                ctx.marketplace.markBorrowed(user, borrow.id());
                                shell.show(build());
                            } catch (ServiceException e) {
                                Ui.error(e.getMessage());
                            }
                        });
                        browRow.getChildren().add(markBorrowed);
                    } else if (borrow.status() == PeerBorrowStatus.BORROWED) {
                        Button markReturned = new Button("Mark Returned");
                        markReturned.getStyleClass().add("button-teal");
                        markReturned.setOnAction(event -> {
                            try {
                                ctx.marketplace.markReturned(user, borrow.id());
                                shell.show(build());
                            } catch (ServiceException e) {
                                Ui.error(e.getMessage());
                            }
                        });
                        browRow.getChildren().add(markReturned);
                    }
                    ownerSection.getChildren().add(browRow);
                }
            }

            // Pause / Remove buttons
            HBox listingActions = new HBox(12);
            Button chatInbox = new Button("Open Chat Thread");
            chatInbox.getStyleClass().add("button-secondary");
            chatInbox.setOnAction(event -> shell.show(new ChatView(ctx, shell, listingId).build()));

            Button pause = new Button("Pause listing");
            pause.getStyleClass().add("button-secondary");
            pause.setOnAction(event -> {
                ctx.marketplace.pauseListing(user, listingId);
                shell.go(Screen.MARKETPLACE, "Listing paused.");
            });

            Button remove = new Button("Remove listing");
            remove.getStyleClass().add("button-secondary");
            remove.setOnAction(event -> {
                ctx.marketplace.removeListing(user, listingId);
                shell.go(Screen.MARKETPLACE, "Listing removed.");
            });

            listingActions.getChildren().addAll(chatInbox, pause, remove);
            ownerSection.getChildren().add(listingActions);
            page.getChildren().add(ownerSection);
        } else {
            HBox actionRow = new HBox(12);
            Button messageOwner = new Button("Message Owner");
            messageOwner.getStyleClass().add("button-secondary");
            messageOwner.setOnAction(event -> shell.show(new ChatView(ctx, shell, listingId).build()));

            Button requestBorrow = new Button("Request to Borrow");
            requestBorrow.getStyleClass().add("button-primary");
            requestBorrow.setOnAction(event -> MarketplaceDialogs.requestBorrow(ctx, listing, () -> shell.show(build())));

            actionRow.getChildren().addAll(messageOwner, requestBorrow);
            page.getChildren().add(actionRow);
        }

        return page;
    }
}
