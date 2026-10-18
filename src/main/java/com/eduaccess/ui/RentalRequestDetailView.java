package com.eduaccess.ui;

import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.RentalRequestStatus;
import com.eduaccess.model.User;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Staff view of one rental request: its facts on the left, and every offer on the right with the action its
 * status allows (approve or decline, check in or mark as not handed over, return to the owner).
 */
final class RentalRequestDetailView {

    private final AppContext ctx;
    private final AppShell shell;
    private final long requestId;

    RentalRequestDetailView(AppContext ctx, AppShell shell, long requestId) {
        this.ctx = ctx;
        this.shell = shell;
        this.requestId = requestId;
    }

    Node build() {
        User staff = ctx.session.require();
        RentalRequest request = ctx.rentals.request(requestId);
        List<RentalOffer> offers = ctx.rentals.offersFor(staff, requestId);

        Button back = new Button("Back to Library Rental Requests", Icons.ARROW_LEFT.node(16));
        back.getStyleClass().add("button-ghost");
        back.setOnAction(event -> shell.go(Screen.RENTAL_REQUESTS));

        VBox summary = RentalRequestsView.cardShell(request, Ui.requestBadge(request.status()), true);
        summary.getStyleClass().remove("card-hover");
        summary.setMinWidth(RentalRequestsView.CARD_WIDTH);

        VBox list = new VBox(12);
        if (offers.isEmpty()) {
            list.getChildren().add(Ui.muted("No offers yet. Students see this request on their board."));
        }
        for (RentalOffer offer : offers) {
            list.getChildren().add(offerRow(request, offer));
        }
        VBox right = Ui.card(
                Ui.row(10, Ui.section("Offers"), Ui.grow(), Ui.muted(offers.size() + " total")),
                Ui.muted("Approve offers until the request is filled. Inspect each item at the counter on check-in "
                        + "and again when you hand it back."),
                list);
        HBox.setHgrow(right, Priority.ALWAYS);

        return new VBox(16, back, new HBox(20, summary, right));
    }

    private Node offerRow(RentalRequest request, RentalOffer offer) {
        Label item = new Label(offer.itemName() + (offer.brandModel() == null ? "" : " · " + offer.brandModel()));
        item.getStyleClass().add("item-title");
        Label owner = Ui.muted(offer.ownerName() + "  ·  " + offer.ownerSchoolId() + "  ·  Condition: "
                + offer.condition().dbValue());
        owner.getStyleClass().add("small");
        VBox text = new VBox(3, item, owner);
        if (offer.description() != null) {
            text.getChildren().add(small(offer.description()));
        }
        if (offer.ownerNote() != null) {
            text.getChildren().add(small("Note: " + offer.ownerNote()));
        }
        if (offer.conditionIn() != null) {
            String trip = "Arrived " + offer.conditionIn().dbValue()
                    + (offer.conditionOut() == null ? "" : ", returned " + offer.conditionOut().dbValue())
                    + (offer.agreedFee() == null || offer.agreedFee().signum() == 0 ? ""
                    : offer.feePaid() ? ", fee paid" : ", fee not paid yet");
            text.getChildren().add(small(trip));
        }
        if (offer.declineReason() != null) {
            text.getChildren().add(small(offer.declineReason()));
        }
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox actions = Ui.row(8);
        boolean open = request.status() == RentalRequestStatus.OPEN;
        switch (offer.status()) {
            case OFFERED -> {
                if (open) {
                    actions.getChildren().add(action("Approve", "button-primary", () -> {
                        ctx.rentals.approve(ctx.session.require(), offer.id());
                        reload("Approved. " + offer.ownerName() + " will be asked to bring the item to "
                                + request.dropoffPoint() + ".");
                    }));
                }
                actions.getChildren().add(action("Decline", "button-danger-outline", () ->
                        RentalDialogs.decline(ctx, offer, () -> reload("Offer declined."))));
            }
            case APPROVED -> {
                actions.getChildren().add(action("Check in", "button-primary", () ->
                        RentalDialogs.checkIn(ctx, offer, () -> reload("Checked in. The item is with the library."))));
                actions.getChildren().add(action("Not handed over", "button-secondary", () -> {
                    if (Ui.confirm("Mark as not handed over?", "Use this when the owner did not bring the item. "
                            + "The request reopens if there is still time for offers.")) {
                        ctx.rentals.markNoShow(ctx.session.require(), offer.id(), LocalDate.now());
                        reload("Marked as not handed over.");
                    }
                }));
            }
            case CHECKED_IN -> actions.getChildren().add(action("Return to owner", "button-primary", () ->
                    RentalDialogs.returnToOwner(ctx, offer, () -> reload("Returned to " + offer.ownerName() + "."))));
            default -> {
                // Final states have no actions.
            }
        }

        VBox side = new VBox(10, Ui.offerBadge(offer.status()), actions);
        side.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        HBox row = new HBox(14, text, side);
        row.getStyleClass().add("card-flat");
        if (offer.status() == OfferStatus.OFFERED) {
            row.setStyle("-fx-border-color: -ea-amber-line; -fx-background-color: -ea-amber-soft;");
        }
        return row;
    }

    private Button action(String text, String style, Runnable work) {
        Button button = new Button(text);
        button.getStyleClass().addAll(style, "button-small");
        button.setOnAction(event -> Ui.run(work));
        return button;
    }

    private void reload(String message) {
        VBox page = new VBox(16, Ui.banner(message, "success"), build());
        shell.show(page);
    }

    private static Label small(String text) {
        Label label = Ui.muted(text);
        label.getStyleClass().add("small");
        return label;
    }
}
