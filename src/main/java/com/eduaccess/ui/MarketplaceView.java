package com.eduaccess.ui;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.MarketplaceCategory;
import com.eduaccess.model.PeerBorrow;
import com.eduaccess.model.PeerBorrowStatus;
import com.eduaccess.model.PeerMessage;
import com.eduaccess.model.StudentListing;
import com.eduaccess.model.User;
import com.eduaccess.service.MarketplaceService;
import com.eduaccess.service.ServiceException;
import java.math.BigDecimal;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The Student Marketplace: peer-to-peer student lending, borrowing, and chat board. */
public final class MarketplaceView {

    private final AppContext ctx;
    private final AppShell shell;

    private final TextField searchField = new TextField();
    private final ComboBox<EducationLevel> levelFilter = new ComboBox<>();
    private final VBox listingContainer = new VBox(16);
    private final ToggleGroup tabGroup = new ToggleGroup();
    private String currentTab = "Browse";

    public MarketplaceView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    public Node build() {
        User user = ctx.session.require();
        if (!MarketplaceService.mayTrade(user) || ctx.marketplace.isMinor(user)) {
            return restrictedNotice();
        }

        VBox page = new VBox(20);
        page.setFillWidth(true);

        // Header & Post button
        Label title = Ui.heading("Student Marketplace");
        Label subtitle = Ui.muted("Lend and borrow directly with other students. The library is not involved.");
        Button postButton = new Button("+ Post an item");
        postButton.getStyleClass().add("button-teal");
        postButton.setOnAction(event -> MarketplaceDialogs.postListing(ctx, () -> shell.go(Screen.MARKETPLACE)));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerRow = Ui.row(16, Ui.column(4, title, subtitle), spacer, postButton);
        page.getChildren().add(headerRow);

        // Tabs
        HBox tabs = new HBox(10);
        for (String tabName : List.of("Browse", "My listings", "My borrowings", "Messages")) {
            ToggleButton btn = new ToggleButton(tabName);
            btn.getStyleClass().add("toggle-tab");
            btn.setToggleGroup(tabGroup);
            if (tabName.equals(currentTab)) {
                btn.setSelected(true);
            }
            btn.setOnAction(event -> {
                currentTab = tabName;
                refreshContent(user);
            });
            tabs.getChildren().add(btn);
        }
        page.getChildren().add(tabs);

        refreshContent(user);
        page.getChildren().add(listingContainer);
        return page;
    }

    private Node restrictedNotice() {
        VBox box = new VBox(16,
                Ui.heading("Student Marketplace Restricted"),
                Ui.banner("The Student Marketplace is available exclusively to Senior High School and "
                        + "College students who are 18 years of age or older. Elementary and Junior High "
                        + "students, as well as faculty and admin staff, cannot participate in peer lending.", "warning")
        );
        box.setPadding(new Insets(20));
        return box;
    }

    private void refreshContent(User user) {
        listingContainer.getChildren().clear();
        switch (currentTab) {
            case "Browse" -> buildBrowseView(user);
            case "My listings" -> buildMyListingsView(user);
            case "My borrowings" -> buildMyBorrowingsView(user);
            case "Messages" -> buildMessagesView(user);
            default -> {}
        }
    }

    private void buildBrowseView(User user) {
        HBox filters = new HBox(12);
        searchField.setPromptText("Search listings...");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, was, now) -> updateBrowseResults(user));

        levelFilter.getItems().setAll(EducationLevel.values());
        levelFilter.setPromptText("All levels");
        levelFilter.setOnAction(event -> updateBrowseResults(user));
        Button clearFilters = new Button("Clear");
        clearFilters.getStyleClass().add("button-secondary");
        clearFilters.setOnAction(event -> {
            searchField.clear();
            levelFilter.setValue(null);
            updateBrowseResults(user);
        });

        filters.getChildren().addAll(searchField, levelFilter, clearFilters);
        listingContainer.getChildren().add(filters);

        updateBrowseResults(user);
    }

    private void updateBrowseResults(User user) {
        // If there are more than filters + listings, keep filter at index 0 and listings container updated
        if (listingContainer.getChildren().size() > 1) {
            listingContainer.getChildren().remove(1, listingContainer.getChildren().size());
        }
        List<StudentListing> listings = ctx.marketplace.browse(
                searchField.getText(), null, levelFilter.getValue());

        if (listings.isEmpty()) {
            listingContainer.getChildren().add(Ui.muted("No active marketplace listings found."));
            return;
        }

        FlowPane grid = new FlowPane(16, 16);
        for (StudentListing listing : listings) {
            grid.getChildren().add(listingCard(listing, user));
        }
        listingContainer.getChildren().add(grid);
    }

    private Node listingCard(StudentListing listing, User user) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPrefWidth(260);
        card.setMinWidth(260);

        Label cat = Ui.muted(listing.category().dbValue() + " • " + listing.educationLevel().dbValue());
        Label name = Ui.heading(listing.title());
        name.setStyle("-fx-font-size: 15px;");
        Label fee = new Label(listing.dailyFee().compareTo(BigDecimal.ZERO) == 0 ? "Free to borrow"
                : "₱" + listing.dailyFee().toPlainString() + " / day");
        fee.getStyleClass().add("badge-teal");

        Label pickup = Ui.muted("Pickup: " + listing.pickupPoint());

        Button view = new Button("View details");
        view.getStyleClass().add("button-primary");
        view.setMaxWidth(Double.MAX_VALUE);
        view.setOnAction(event -> shell.show(new ListingDetailView(ctx, shell, listing.id()).build()));

        card.getChildren().addAll(cat, name, fee, pickup, view);
        return card;
    }

    private void buildMyListingsView(User user) {
        List<StudentListing> listings = ctx.marketplace.myListings(user);
        if (listings.isEmpty()) {
            listingContainer.getChildren().add(Ui.muted("You have not posted any marketplace listings yet. Click '+ Post an item' above to start."));
            return;
        }

        VBox list = new VBox(12);
        for (StudentListing listing : listings) {
            HBox row = new HBox(16);
            row.getStyleClass().add("card-flat");
            row.setAlignment(Pos.CENTER_LEFT);

            Label title = Ui.heading(listing.title());
            Label status = new Label(listing.status());
            status.getStyleClass().add("badge-blue");
            Label fee = new Label("₱" + listing.dailyFee().toPlainString() + "/day");

            Region r = new Region();
            HBox.setHgrow(r, Priority.ALWAYS);

            Button view = new Button("Manage / Requests");
            view.getStyleClass().add("button-secondary");
            view.setOnAction(event -> shell.show(new ListingDetailView(ctx, shell, listing.id()).build()));

            row.getChildren().addAll(Ui.column(4, title, fee), status, r, view);
            list.getChildren().add(row);
        }
        listingContainer.getChildren().add(list);
    }

    private void buildMyBorrowingsView(User user) {
        List<PeerBorrow> borrows = ctx.marketplace.myBorrowings(user);
        if (borrows.isEmpty()) {
            listingContainer.getChildren().add(Ui.muted("You have no active or past borrow requests."));
            return;
        }

        VBox list = new VBox(12);
        for (PeerBorrow borrow : borrows) {
            StudentListing listing = ctx.marketplace.getListing(borrow.listingId());
            HBox row = new HBox(16);
            row.getStyleClass().add("card-flat");
            row.setAlignment(Pos.CENTER_LEFT);

            Label title = Ui.heading(listing.title());
            Label status = new Label(borrow.status().dbValue());
            status.getStyleClass().add("badge-amber");
            Label note = Ui.muted("Note: " + (borrow.note() != null ? borrow.note() : "None"));

            Region r = new Region();
            HBox.setHgrow(r, Priority.ALWAYS);

            Button chat = new Button("Chat owner");
            chat.getStyleClass().add("button-secondary");
            chat.setOnAction(event -> shell.show(new ChatView(ctx, shell, listing.id()).build()));

            row.getChildren().addAll(Ui.column(4, title, note), status, r, chat);
            if (borrow.status() == PeerBorrowStatus.REQUESTED) {
                Button cancel = new Button("Cancel");
                cancel.getStyleClass().add("button-secondary");
                cancel.setOnAction(event -> {
                    try {
                        ctx.marketplace.cancelBorrow(user, borrow.id());
                        refreshContent(user);
                    } catch (ServiceException e) {
                        Ui.error(e.getMessage());
                    }
                });
                row.getChildren().add(cancel);
            }
            list.getChildren().add(row);
        }
        listingContainer.getChildren().add(list);
    }

    private void buildMessagesView(User user) {
        List<StudentListing> conversations = ctx.marketplace.conversations(user);
        if (conversations.isEmpty()) {
            listingContainer.getChildren().add(Ui.muted("You have no messages or conversations yet."));
            return;
        }

        VBox list = new VBox(12);
        for (StudentListing listing : conversations) {
            HBox row = new HBox(16);
            row.getStyleClass().add("card-flat");
            row.setAlignment(Pos.CENTER_LEFT);

            Label title = Ui.heading(listing.title());
            Label hint = Ui.muted("Conversation regarding this listing");

            Region r = new Region();
            HBox.setHgrow(r, Priority.ALWAYS);

            Button open = new Button("Open chat");
            open.getStyleClass().add("button-primary");
            open.setOnAction(event -> shell.show(new ChatView(ctx, shell, listing.id()).build()));

            row.getChildren().addAll(Ui.column(4, title, hint), r, open);
            list.getChildren().add(row);
        }
        listingContainer.getChildren().add(list);
    }
}
