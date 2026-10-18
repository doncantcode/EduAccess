package com.eduaccess.ui;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.model.Catalog;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The physical catalog as a card grid with filters. One class serves both tabs: the Physical Library (books and
 * reading material) and the Physical Hub (equipment). Which items appear follows {@link ItemCategory#catalog()}.
 */
final class CatalogView {

    private static final String AVAILABLE_FIRST = "Available first";
    private static final String NOT_AVAILABLE_FIRST = "Not available first";

    private final AppContext ctx;
    private final AppShell shell;
    private final Catalog catalog;
    private final Map<ItemCategory, CheckBox> categoryChecks = new LinkedHashMap<>();
    private final ComboBox<EducationLevel> level = new ComboBox<>();
    private final ComboBox<ItemStatus> status = new ComboBox<>();
    private final ComboBox<String> sort = new ComboBox<>();
    private final TextField search = new TextField();
    private final FlowPane grid = new FlowPane(18, 18);
    private final Label count = Ui.muted("");

    CatalogView(AppContext ctx, AppShell shell, Catalog catalog) {
        this.ctx = ctx;
        this.shell = shell;
        this.catalog = catalog;
    }

    /** The full Physical Hub page, with its own heading. */
    Node build() {
        Node[] actions = isAdmin() ? new Node[] {addButton("Add item")} : new Node[0];
        HBox header = Ui.pageHeader("Physical Hub",
                "Borrow calculators, laptops, tablets and lab kits shared by the community.", actions);
        return new VBox(22, header, body());
    }

    /** Filters and the card grid, without a heading. The Physical Library tab embeds this. */
    Node body() {
        VBox filters = filterPanel();
        filters.setMinWidth(230);
        filters.setPrefWidth(230);
        filters.setMaxWidth(230);
        filters.setMaxHeight(Region.USE_PREF_SIZE);

        search.setPromptText(catalog == Catalog.LIBRARY
                ? "Search titles, editions, modules..."
                : "Search calculators, laptops, kits...");
        search.textProperty().addListener((obs, was, now) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        sort.setItems(FXCollections.observableArrayList(AVAILABLE_FIRST, NOT_AVAILABLE_FIRST));
        sort.setValue(AVAILABLE_FIRST);
        sort.setPrefWidth(190);
        sort.valueProperty().addListener((obs, was, now) -> refresh());

        HBox toolbar = Ui.row(12, search, count, sort);
        VBox main = new VBox(16, toolbar, grid);
        HBox.setHgrow(main, Priority.ALWAYS);

        configureStatusAndLevel();
        refresh();
        HBox layout = new HBox(22, filters, main);
        return layout;
    }

    Button addButton(String text) {
        Button add = new Button(text, Icons.PLUS.node(16));
        add.getStyleClass().add("button-primary");
        Screen back = catalog == Catalog.LIBRARY ? Screen.PHYSICAL_LIBRARY : Screen.CATALOG;
        add.setOnAction(event -> AdminForms.addPhysicalItem(ctx, () -> shell.go(back)));
        return add;
    }

    private VBox filterPanel() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        box.getChildren().add(Ui.section("Filters"));
        List<ItemCategory> categories = catalog.categories();
        if (categories.size() > 1) {
            box.getChildren().add(Ui.fieldLabel("Category"));
            for (ItemCategory category : categories) {
                CheckBox check = new CheckBox(category.dbValue());
                check.setSelected(true);
                check.selectedProperty().addListener((obs, was, now) -> refresh());
                categoryChecks.put(category, check);
                box.getChildren().add(check);
            }
        }
        level.setMaxWidth(Double.MAX_VALUE);
        status.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(
                Ui.labelled("Education level", level),
                Ui.labelled("Availability", status));

        Button clear = new Button("Clear filters");
        clear.getStyleClass().addAll("button-secondary", "button-block");
        clear.setOnAction(event -> clearFilters());
        box.getChildren().add(clear);
        return box;
    }

    private void configureStatusAndLevel() {
        level.getItems().add(null);
        level.getItems().addAll(EducationLevel.values());
        level.setConverter(new StringConverter<>() {
            @Override
            public String toString(EducationLevel value) {
                return value == null ? "All levels" : value.dbValue();
            }

            @Override
            public EducationLevel fromString(String text) {
                return null;
            }
        });
        level.valueProperty().addListener((obs, was, now) -> refresh());

        status.getItems().add(null);
        status.getItems().addAll(ItemStatus.AVAILABLE, ItemStatus.BORROWED, ItemStatus.OVERDUE, ItemStatus.RETURNED);
        status.setConverter(new StringConverter<>() {
            @Override
            public String toString(ItemStatus value) {
                return value == null ? "All statuses" : value.dbValue();
            }

            @Override
            public ItemStatus fromString(String text) {
                return null;
            }
        });
        status.valueProperty().addListener((obs, was, now) -> refresh());
    }

    private void clearFilters() {
        categoryChecks.values().forEach(check -> check.setSelected(true));
        level.setValue(null);
        status.setValue(null);
        search.clear();
    }

    private void refresh() {
        List<ItemCategory> chosen = new ArrayList<>();
        if (categoryChecks.isEmpty()) {
            chosen.addAll(catalog.categories());
        } else {
            categoryChecks.forEach((category, check) -> {
                if (check.isSelected()) {
                    chosen.add(category);
                }
            });
        }
        // An empty category list means "all categories" to the DAO, which would leak the other tab's items.
        List<PhysicalItem> items = chosen.isEmpty() ? new ArrayList<>() : new ArrayList<>(ctx.inventory.browse(
                new ItemFilter(chosen, level.getValue(), status.getValue(), search.getText())));
        items.sort(availabilityOrder());
        count.setText(items.size() + (items.size() == 1 ? " item" : " items"));
        if (items.isEmpty()) {
            VBox empty = Ui.card(Ui.section("Nothing matches these filters"),
                    Ui.muted("Try another search word, or clear the filters."));
            empty.setPrefWidth(420);
            grid.getChildren().setAll(empty);
        } else {
            grid.getChildren().setAll(items.stream().map(this::card).toList());
        }
    }

    /** Available items sort before the rest. The sort control can put the rest first instead. */
    private Comparator<PhysicalItem> availabilityOrder() {
        Comparator<PhysicalItem> available = Comparator.comparing(
                (PhysicalItem item) -> item.status() == ItemStatus.AVAILABLE ? 0 : 1);
        return NOT_AVAILABLE_FIRST.equals(sort.getValue()) ? available.reversed() : available;
    }

    private VBox card(PhysicalItem item) {
        Label pill = new Label(item.category().dbValue().toUpperCase());
        pill.getStyleClass().add("category-pill");
        pill.setMinWidth(Label.USE_PREF_SIZE);
        Label title = new Label(item.title());
        title.getStyleClass().add("item-title");
        title.setWrapText(true);
        title.setMinHeight(Label.USE_PREF_SIZE);
        Label brand = Ui.muted(item.brandModel() == null ? item.category().dbValue() : item.brandModel());
        Label detail = Ui.muted(Ui.levelText(item.levels()) + "  ·  " + item.condition().dbValue());
        detail.getStyleClass().add("small");

        Button view = new Button("View");
        view.getStyleClass().addAll("button-secondary", "button-small");
        view.setAccessibleText("View " + item.title());
        view.setOnAction(event -> shell.show(new ItemDetailView(ctx, shell, item.id()).build()));
        HBox footer = Ui.row(8, Ui.itemBadge(item.status()), Ui.grow(), view);

        VBox text = new VBox(4, pill, title, brand, detail);
        VBox.setVgrow(text, Priority.ALWAYS);
        VBox card = new VBox(12, Ui.art(item.category(), 212, 118), text, footer);
        card.getStyleClass().addAll("card", "item-card", "card-hover");
        card.setPrefWidth(240);
        card.setMaxWidth(240);
        return card;
    }

    private boolean isAdmin() {
        return ctx.session.require().role() == Role.ADMIN;
    }
}
