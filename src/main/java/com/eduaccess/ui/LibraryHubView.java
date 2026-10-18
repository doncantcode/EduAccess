package com.eduaccess.ui;

import com.eduaccess.model.Catalog;
import com.eduaccess.model.Role;
import java.time.LocalDate;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The library page with two tabs: the Physical Library (books on the shelves) and Library Rental Requests (items
 * the library wants to rent from students). Each tab has its own sidebar entry, and the tabs switch between them,
 * so the sidebar highlight always matches the tab.
 */
final class LibraryHubView {

    enum Tab { BOOKS, RENTALS }

    private final AppContext ctx;
    private final AppShell shell;
    private final Tab tab;

    LibraryHubView(AppContext ctx, AppShell shell, Tab tab) {
        this.ctx = ctx;
        this.shell = shell;
        this.tab = tab;
    }

    Node build() {
        boolean admin = ctx.session.require().role() == Role.ADMIN;
        CatalogView books = new CatalogView(ctx, shell, Catalog.LIBRARY);
        RentalRequestsView rentals = new RentalRequestsView(ctx, shell);

        Node header;
        Node body;
        if (tab == Tab.BOOKS) {
            Node[] actions = admin ? new Node[] {books.addButton("Add book")} : new Node[0];
            header = Ui.pageHeader("Physical Library",
                    "Textbooks, learner modules and reading material you can borrow from the shelves.", actions);
            body = books.body();
        } else {
            Node[] actions = admin ? new Node[] {createButton()} : new Node[0];
            header = Ui.pageHeader("Library Rental Requests", admin
                    ? "Post what the library needs to rent for an event, then review what students offer."
                    : "The library needs these items for a short time. Offer yours and get it back after the event.",
                    actions);
            body = rentals.build();
        }
        return new VBox(18, header, tabs(), body);
    }

    private Node tabs() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton books = tab("Physical Library", Icons.BOOK, null, group);
        int open = ctx.rentals.openRequests(LocalDate.now()).size();
        ToggleButton rentals = tab("Library Rental Requests", Icons.SWAP, open > 0 ? String.valueOf(open) : null,
                group);
        (tab == Tab.BOOKS ? books : rentals).setSelected(true);
        books.setOnAction(event -> shell.go(Screen.PHYSICAL_LIBRARY));
        rentals.setOnAction(event -> shell.go(Screen.RENTAL_REQUESTS));
        HBox bar = new HBox(books, rentals);
        bar.getStyleClass().add("page-tabs");
        return bar;
    }

    private ToggleButton tab(String text, Icons icon, String count, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("page-tab");
        button.setToggleGroup(group);
        if (count == null) {
            button.setGraphic(icon.node(18));
        } else {
            Label pill = new Label(count);
            pill.getStyleClass().add("tab-count");
            HBox graphic = Ui.row(8, icon.node(18), new Label(text), pill);
            graphic.getChildren().get(1).getStyleClass().add("tab-text");
            button.setGraphic(graphic);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
        return button;
    }

    private Button createButton() {
        Button create = new Button("Create New Rental Request", Icons.PLUS.node(16));
        create.getStyleClass().add("button-primary");
        create.setOnAction(event -> RentalDialogs.createRequest(ctx, id ->
                shell.go(Screen.RENTAL_REQUESTS, "Your rental request is posted. Students can offer items now.")));
        return create;
    }
}
