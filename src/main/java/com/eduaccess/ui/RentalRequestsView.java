package com.eduaccess.ui;

import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalCategory;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.RentalRequestStatus;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.RentalService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The Library Rental Requests board.
 * <ul>
 *   <li>Students see the open requests as cards, each with an <b>Offer Item for Rent</b> button, and their own
 *       offers underneath.</li>
 *   <li>Staff see every active request with its review queue, plus past requests. Creating a request is in the
 *       page header ({@link LibraryHubView}).</li>
 * </ul>
 */
final class RentalRequestsView {

    static final double CARD_WIDTH = 340;

    private final AppContext ctx;
    private final AppShell shell;
    private final LocalDate today = LocalDate.now();
    private final FlowPane grid = new FlowPane(18, 18);
    private final TextField search = new TextField();
    private final ToggleGroup chips = new ToggleGroup();
    private List<RentalRequest> requests = List.of();
    private List<RentalOffer> myOffers = List.of();

    RentalRequestsView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        User user = ctx.session.require();
        return user.role() == Role.ADMIN ? staffBoard(user) : studentBoard(user);
    }

    // ----- Student side ----------------------------------------------------------------------------------------

    private Node studentBoard(User user) {
        requests = ctx.rentals.openRequests(today);
        myOffers = ctx.rentals.myOffers(user);

        Label how = Ui.banner("How it works: offer an item you own, bring it to the library counter once staff "
                + "approve it, and collect it after the event with your payment. " + RentalService.AGE_RULE
                + " Bring your school ID: staff check it at hand-over.", "info");

        VBox page = new VBox(18, how, toolbar(), grid);
        refreshGrid();
        if (!myOffers.isEmpty()) {
            page.getChildren().add(myOffersSection());
        }
        return page;
    }

    private Node toolbar() {
        Map<RentalCategory, Long> counts = new EnumMap<>(RentalCategory.class);
        counts.putAll(requests.stream().collect(Collectors.groupingBy(RentalRequest::category, Collectors.counting())));

        HBox chipRow = new HBox(8, chip("All  " + requests.size(), null));
        for (RentalCategory category : RentalCategory.values()) {
            long n = counts.getOrDefault(category, 0L);
            if (n > 0) {
                chipRow.getChildren().add(chip(category.dbValue() + "  " + n, category));
            }
        }
        chips.getToggles().get(0).setSelected(true);
        chips.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                was.setSelected(true);
            } else {
                refreshGrid();
            }
        });

        search.setPromptText("Search requests...");
        search.setPrefWidth(260);
        search.textProperty().addListener((obs, was, now) -> refreshGrid());
        HBox bar = new HBox(12, chipRow, Ui.grow(), search);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private ToggleButton chip(String text, RentalCategory category) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("chip");
        chip.setUserData(category);
        chip.setToggleGroup(chips);
        return chip;
    }

    private void refreshGrid() {
        RentalCategory category = chips.getSelectedToggle() == null ? null
                : (RentalCategory) chips.getSelectedToggle().getUserData();
        String text = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        List<RentalRequest> shown = requests.stream()
                .filter(r -> category == null || r.category() == category)
                .filter(r -> text.isEmpty() || (r.title() + " " + r.purpose() + " " + r.description())
                        .toLowerCase(Locale.ROOT).contains(text))
                .toList();
        if (shown.isEmpty()) {
            VBox empty = Ui.card(Ui.section(requests.isEmpty()
                            ? "The library doesn't need anything right now"
                            : "No requests match"),
                    Ui.muted(requests.isEmpty()
                            ? "Check back later. New requests appear here and in your notifications."
                            : "Try another category or search word."));
            empty.setPrefWidth(460);
            grid.getChildren().setAll(empty);
        } else {
            grid.getChildren().setAll(shown.stream().map(this::studentCard).toList());
        }
    }

    private Node studentCard(RentalRequest request) {
        Optional<RentalOffer> mine = myOffers.stream()
                .filter(o -> o.requestId() == request.id() && o.isLive())
                .findFirst();
        VBox card = cardShell(request, deadlineBadge(request), false);

        Node footer;
        if (mine.isPresent()) {
            RentalOffer offer = mine.get();
            Label yours = Ui.muted("You offered: " + offer.itemName());
            yours.getStyleClass().add("small");
            HBox row = Ui.row(8, Ui.offerBadge(offer.status()), Ui.grow());
            if (offer.status() == OfferStatus.OFFERED || offer.status() == OfferStatus.APPROVED) {
                row.getChildren().add(withdrawButton(offer));
            }
            footer = new VBox(8, yours, row);
            card.getStyleClass().add("request-mine");
        } else if (!RentalService.mayOffer(ctx.session.require())) {
            Label rule = Ui.muted("Offers are open to students 18 or older.");
            rule.getStyleClass().add("small");
            footer = rule;
        } else {
            Button offer = new Button("Offer Item for Rent", Icons.SWAP.node(18));
            offer.getStyleClass().addAll("button-primary", "button-block");
            offer.setDefaultButton(false);
            offer.setAccessibleText("Offer an item for: " + request.title());
            offer.setOnAction(event -> RentalDialogs.offerItem(ctx, request, () -> shell.go(Screen.RENTAL_REQUESTS,
                    "Thanks! Your offer was sent. You'll see it under \"Your offers\" and get a notification "
                            + "when the library approves it.")));
            footer = offer;
        }
        card.getChildren().add(footer);
        return card;
    }

    private Node myOffersSection() {
        VBox list = new VBox(10);
        for (RentalOffer offer : myOffers) {
            Label item = new Label(offer.itemName() + (offer.brandModel() == null ? "" : " · " + offer.brandModel()));
            item.getStyleClass().add("item-title");
            Label forRequest = Ui.muted("For: " + offer.requestTitle());
            forRequest.getStyleClass().add("small");
            VBox text = new VBox(2, item, forRequest);
            if (offer.declineReason() != null && !offer.isLive()) {
                Label reason = Ui.muted(offer.declineReason());
                reason.getStyleClass().add("small");
                text.getChildren().add(reason);
            }
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = Ui.row(14, text, Ui.offerBadge(offer.status()));
            if (offer.status() == OfferStatus.OFFERED || offer.status() == OfferStatus.APPROVED) {
                row.getChildren().add(withdrawButton(offer));
            }
            row.getStyleClass().add("card-flat");
            list.getChildren().add(row);
        }
        return Ui.card(Ui.section("Your offers"),
                Ui.muted("Everything you have offered to the library, newest first."), list);
    }

    private Button withdrawButton(RentalOffer offer) {
        Button withdraw = new Button("Withdraw");
        withdraw.getStyleClass().addAll("button-danger-outline", "button-small");
        withdraw.setOnAction(event -> {
            if (Ui.confirm("Withdraw your offer?", "The library will no longer count on your "
                    + offer.itemName() + " for \"" + offer.requestTitle() + "\".")) {
                Ui.run(() -> {
                    ctx.rentals.withdraw(ctx.session.require(), offer.id(), today);
                    shell.go(Screen.RENTAL_REQUESTS, "Your offer was withdrawn.");
                });
            }
        });
        return withdraw;
    }

    // ----- Staff side ------------------------------------------------------------------------------------------

    private Node staffBoard(User staff) {
        List<RentalRequest> all = ctx.rentals.allRequests(today);
        List<RentalRequest> active = all.stream()
                .filter(r -> r.status() == RentalRequestStatus.OPEN || r.status() == RentalRequestStatus.FILLED)
                .toList();
        List<RentalRequest> past = all.stream().filter(r -> !active.contains(r)).toList();
        int toReview = active.stream().mapToInt(RentalRequest::pendingCount).sum();

        HBox metrics = new HBox(14,
                Ui.metric("Open requests",
                        String.valueOf(active.stream().filter(r -> r.status() == RentalRequestStatus.OPEN).count()),
                        "blue"),
                Ui.metric("Offers to review", String.valueOf(toReview), "amber"),
                Ui.metric("Filled, waiting for the event",
                        String.valueOf(active.stream().filter(r -> r.status() == RentalRequestStatus.FILLED).count()),
                        "teal"),
                Ui.metric("Completed", String.valueOf(
                        past.stream().filter(r -> r.status() == RentalRequestStatus.COMPLETED).count()), "navy"));

        VBox page = new VBox(18, metrics);
        if (toReview > 0) {
            page.getChildren().add(Ui.banner(toReview + " offer(s) are waiting for your review.", "warning"));
        }
        page.getChildren().add(Ui.section("Active requests"));
        if (active.isEmpty()) {
            page.getChildren().add(Ui.card(Ui.section("No active requests"),
                    Ui.muted("Use \"Create New Rental Request\" to ask students for an item.")));
        } else {
            grid.getChildren().setAll(active.stream().map(this::staffCard).toList());
            page.getChildren().add(grid);
        }
        if (!past.isEmpty()) {
            page.getChildren().addAll(Ui.section("Past requests"), pastList(past));
        }
        return page;
    }

    private Node staffCard(RentalRequest request) {
        VBox card = cardShell(request, Ui.requestBadge(request.status()), true);
        String reviewText = request.pendingCount() > 0
                ? "Review offers (" + request.pendingCount() + ")"
                : "Manage offers";
        Button review = new Button(reviewText);
        review.getStyleClass().add(request.pendingCount() > 0 ? "button-primary" : "button-secondary");
        HBox.setHgrow(review, Priority.ALWAYS);
        review.setMaxWidth(Double.MAX_VALUE);
        review.setOnAction(event -> shell.show(new RentalRequestDetailView(ctx, shell, request.id()).build()));
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("button-danger-outline");
        cancel.setOnAction(event -> {
            if (Ui.confirm("Cancel \"" + request.title() + "\"?", "Waiting offers are declined and approved ones "
                    + "cancelled. Items already with the library still need to be returned to their owners.")) {
                Ui.run(() -> {
                    ctx.rentals.cancelRequest(ctx.session.require(), request.id());
                    shell.go(Screen.RENTAL_REQUESTS, "The request was cancelled and students were updated.");
                });
            }
        });
        card.getChildren().add(Ui.row(10, review, cancel));
        return card;
    }

    private Node pastList(List<RentalRequest> past) {
        VBox list = new VBox(8);
        for (RentalRequest r : past) {
            Label title = new Label(r.title());
            title.getStyleClass().add("item-title");
            Label meta = Ui.muted(r.purpose() + "  ·  " + Ui.dateRange(r.neededFrom(), r.neededUntil())
                    + "  ·  " + r.approvedCount() + " of " + r.quantityNeeded() + " secured");
            meta.getStyleClass().add("small");
            VBox text = new VBox(2, title, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            Button open = new Button("Details");
            open.getStyleClass().addAll("button-ghost", "button-small");
            open.setOnAction(event -> shell.show(new RentalRequestDetailView(ctx, shell, r.id()).build()));
            HBox row = Ui.row(12, Ui.rentalArt(r.category(), 40, 40), text, Ui.requestBadge(r.status()), open);
            row.getStyleClass().add("card-flat");
            list.getChildren().add(row);
        }
        return list;
    }

    // ----- Shared card -----------------------------------------------------------------------------------------

    /**
     * Art, category, title, purpose, description, the key facts and progress. Callers add the footer.
     * Staff read the fee as what the library pays, students as what they earn.
     */
    static VBox cardShell(RentalRequest request, Node cornerBadge, boolean staff) {
        Label pill = new Label(request.category().dbValue().toUpperCase(Locale.ROOT));
        pill.getStyleClass().add("category-pill");
        pill.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = Ui.row(12, Ui.rentalArt(request.category(), 44, 44), pill, Ui.grow(), cornerBadge);

        Label title = new Label(request.title());
        title.getStyleClass().add("item-title");
        title.setStyle("-fx-font-size: 17px;");
        title.setWrapText(true);
        Label purpose = Ui.muted("For: " + request.purpose());
        Label description = new Label(request.description());
        description.setWrapText(true);
        description.getStyleClass().add("small");
        description.setStyle("-fx-text-fill: -ea-ink-2;");
        description.setMinHeight(Label.USE_PREF_SIZE);

        VBox card = new VBox(new VBox(4, title, purpose), description, facts(request, staff), progress(request));
        card.getChildren().add(0, top);
        card.getStyleClass().addAll("card", "request-card", "card-hover");
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        return card;
    }

    private static Node facts(RentalRequest r, boolean staff) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("info-grid");
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints icon = new ColumnConstraints();
        icon.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints key = new ColumnConstraints();
        key.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints value = new ColumnConstraints();
        value.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(icon, key, value);
        long days = r.rentalDays();
        fact(grid, 0, Icons.CALENDAR, "Needed",
                Ui.dateRange(r.neededFrom(), r.neededUntil()) + " (" + days + (days == 1 ? " day)" : " days)"));
        fact(grid, 1, Icons.TAG, staff ? "Library pays" : "You earn", feeText(r.rentalFee()));
        fact(grid, 2, Icons.CHECK, "Condition", r.minCondition().dbValue() + " or better");
        fact(grid, 3, Icons.PIN, "Drop-off", r.dropoffPoint());
        return grid;
    }

    private static void fact(GridPane grid, int row, Icons icon, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("info-key");
        Label v = new Label(value);
        v.getStyleClass().add("info-value");
        v.setWrapText(true);
        grid.add(icon.node(16), 0, row);
        grid.add(k, 1, row);
        grid.add(v, 2, row);
    }

    static String feeText(BigDecimal fee) {
        return fee.signum() <= 0 ? "Payment not set" : Ui.money(fee) + " per item";
    }

    private static Node progress(RentalRequest r) {
        double done = Math.min(1.0, (double) r.approvedCount() / r.quantityNeeded());
        ProgressBar bar = new ProgressBar(done);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(8);
        if (done >= 1.0) {
            bar.getStyleClass().add("progress-complete");
        }
        Label text = Ui.muted(r.approvedCount() + " of " + r.quantityNeeded()
                + (r.quantityNeeded() == 1 ? " item secured" : " items secured"));
        text.getStyleClass().add("small");
        return new VBox(6, text, bar);
    }

    /** "Closes today", "Closes in 2 days" and so on, coloured by urgency. */
    private Label deadlineBadge(RentalRequest r) {
        long days = ChronoUnit.DAYS.between(today, r.offersCloseOn());
        String text = days <= 0 ? "Closes today" : days == 1 ? "Closes tomorrow" : "Closes in " + days + " days";
        String tone = days <= 1 ? "urgent" : days <= 3 ? "soon" : "calm";
        return Ui.badge(text, tone);
    }
}
