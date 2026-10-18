package com.eduaccess.ui;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.model.Catalog;
import com.eduaccess.model.DigitalResource;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.ResourceTab;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Results of the header search, grouped by where they live: library, hub, rental requests, digital library. */
final class SearchResultsView {

    private static final int LIMIT = 6;

    private final AppContext ctx;
    private final AppShell shell;
    private final String query;

    SearchResultsView(AppContext ctx, AppShell shell, String query) {
        this.ctx = ctx;
        this.shell = shell;
        this.query = query;
    }

    Node build() {
        List<PhysicalItem> books = items(Catalog.LIBRARY);
        List<PhysicalItem> equipment = items(Catalog.HUB);
        String needle = query.toLowerCase(Locale.ROOT);
        List<RentalRequest> requests = ctx.rentals.openRequests(LocalDate.now()).stream()
                .filter(r -> (r.title() + " " + r.purpose() + " " + r.description()).toLowerCase(Locale.ROOT)
                        .contains(needle))
                .toList();
        List<DigitalResource> digital = new ArrayList<>();
        for (ResourceTab tab : ResourceTab.values()) {
            digital.addAll(ctx.library.browse(tab, null, query));
        }

        int total = books.size() + equipment.size() + requests.size() + digital.size();
        VBox page = new VBox(18, Ui.pageHeader("Search results",
                total + (total == 1 ? " match" : " matches") + " for \"" + query + "\""));
        if (total == 0) {
            page.getChildren().add(Ui.card(Ui.section("No matches"),
                    Ui.muted("Try a shorter word, such as \"calculator\", \"chemistry\" or \"projector\".")));
            return page;
        }
        if (!books.isEmpty()) {
            page.getChildren().add(section("Physical Library", books.size(), Screen.PHYSICAL_LIBRARY,
                    books.stream().limit(LIMIT).map(this::itemRow).toList()));
        }
        if (!equipment.isEmpty()) {
            page.getChildren().add(section("Physical Hub", equipment.size(), Screen.CATALOG,
                    equipment.stream().limit(LIMIT).map(this::itemRow).toList()));
        }
        if (!requests.isEmpty()) {
            page.getChildren().add(section("Library Rental Requests", requests.size(), Screen.RENTAL_REQUESTS,
                    requests.stream().limit(LIMIT).map(this::requestRow).toList()));
        }
        if (!digital.isEmpty()) {
            page.getChildren().add(section("Digital Library", digital.size(), Screen.LIBRARY,
                    digital.stream().limit(LIMIT).map(this::digitalRow).toList()));
        }
        return page;
    }

    private List<PhysicalItem> items(Catalog catalog) {
        return ctx.inventory.browse(new ItemFilter(catalog.categories(), null, null, query));
    }

    private Node section(String title, int count, Screen target, List<Node> rows) {
        Button all = new Button("Open " + title);
        all.getStyleClass().addAll("button-ghost", "button-small");
        all.setOnAction(event -> shell.go(target));
        VBox list = new VBox(8);
        list.getChildren().addAll(rows);
        return Ui.card(Ui.row(10, Ui.section(title), Ui.muted(String.valueOf(count)), Ui.grow(), all), list);
    }

    private Node itemRow(PhysicalItem item) {
        Button view = button("View", () -> shell.show(new ItemDetailView(ctx, shell, item.id()).build()));
        return row(Ui.art(item.category(), 40, 40), item.title(),
                (item.brandModel() == null ? item.category().dbValue() : item.brandModel()),
                Ui.itemBadge(item.status()), view);
    }

    private Node requestRow(RentalRequest r) {
        Button view = button("View", () -> shell.go(Screen.RENTAL_REQUESTS));
        return row(Ui.rentalArt(r.category(), 40, 40), r.title(), "For: " + r.purpose(),
                Ui.requestBadge(r.status()), view);
    }

    private Node digitalRow(DigitalResource resource) {
        Button view = button("Open", () -> shell.go(Screen.LIBRARY));
        return row(Ui.documentArt(resource.tab() == ResourceTab.COLLEGE_REPOSITORY, 40, 40), resource.title(),
                resource.subject() + "  ·  " + resource.level().dbValue(),
                resource.cachedOffline() ? Ui.badge("Cached offline", "cached") : Ui.badge("Online only", "online"),
                view);
    }

    private static HBox row(Node art, String title, String meta, Node badge, Button action) {
        Label name = new Label(title);
        name.getStyleClass().add("item-title");
        Label sub = Ui.muted(meta);
        sub.getStyleClass().add("small");
        VBox text = new VBox(2, name, sub);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = Ui.row(12, art, text, badge, action);
        row.getStyleClass().add("card-flat");
        return row;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button-secondary", "button-small");
        button.setOnAction(event -> action.run());
        return button;
    }
}
