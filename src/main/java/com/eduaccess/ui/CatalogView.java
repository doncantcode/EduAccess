package com.eduaccess.ui;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

/** Physical Hub: the catalog with filters, an availability sort, and (for admins) the add-item action. */
final class CatalogView {

    private static final String AVAILABLE_FIRST = "Available first";
    private static final String NOT_AVAILABLE_FIRST = "Not available first";

    private final AppContext ctx;
    private final AppShell shell;
    private final List<CheckBox> categoryChecks = new ArrayList<>();
    private final ComboBox<EducationLevel> level = new ComboBox<>();
    private final ComboBox<ItemStatus> status = new ComboBox<>();
    private final ComboBox<String> sort = new ComboBox<>();
    private final TextField search = new TextField();
    private final FlowPane grid = new FlowPane(20, 20);
    private final Label count = Ui.muted("");

    CatalogView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        VBox filters = filterPanel();
        filters.setMinWidth(240);
        filters.setPrefWidth(240);

        search.setPromptText("Search calculators, laptops, books...");
        search.textProperty().addListener((obs, was, now) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);

        sort.setItems(FXCollections.observableArrayList(AVAILABLE_FIRST, NOT_AVAILABLE_FIRST));
        sort.setValue(AVAILABLE_FIRST);
        sort.setPrefWidth(190);
        sort.valueProperty().addListener((obs, was, now) -> refresh());
        Label sortLabel = fieldLabel("Sort by");

        HBox toolbar = Ui.row(12, search, sortLabel, sort, count);
        if (ctx.session.require().role() == Role.ADMIN) {
            Button add = new Button("+ Add item");
            add.getStyleClass().add("button-teal");
            add.setOnAction(event -> AdminForms.addPhysicalItem(ctx, () -> shell.go(Screen.CATALOG)));
            toolbar.getChildren().add(add);
        }

        Label title = Ui.heading("Physical Resource Hub");
        Label subtitle = Ui.muted("Borrow calculators, laptops, books and study kits shared by senior students.");
        VBox main = new VBox(18, new VBox(6, title, subtitle), toolbar, grid);
        HBox.setHgrow(main, Priority.ALWAYS);

        configureStatusAndLevel();
        refresh();
        return new HBox(24, filters, main);
    }

    private VBox filterPanel() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        box.getChildren().add(Ui.section("Filters"));
        box.getChildren().add(fieldLabel("Category"));
        for (ItemCategory category : ItemCategory.values()) {
            CheckBox check = new CheckBox(category.dbValue());
            check.setSelected(true);
            check.selectedProperty().addListener((obs, was, now) -> refresh());
            categoryChecks.add(check);
            box.getChildren().add(check);
        }
        box.getChildren().add(fieldLabel("Education level"));
        level.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(level);
        box.getChildren().add(fieldLabel("Availability"));
        status.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(status);

        Button clear = new Button("Clear filters");
        clear.getStyleClass().add("button-secondary");
        clear.setMaxWidth(Double.MAX_VALUE);
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
        categoryChecks.forEach(check -> check.setSelected(true));
        level.setValue(null);
        status.setValue(null);
        search.clear();
    }

    private void refresh() {
        ItemCategory[] all = ItemCategory.values();
        List<ItemCategory> chosen = new ArrayList<>();
        for (int i = 0; i < all.length; i++) {
            if (categoryChecks.get(i).isSelected()) {
                chosen.add(all[i]);
            }
        }
        List<PhysicalItem> items = new ArrayList<>(ctx.inventory.browse(
                new ItemFilter(chosen, level.getValue(), status.getValue(), search.getText())));
        items.sort(availabilityOrder());
        count.setText(items.size() + (items.size() == 1 ? " item" : " items"));
        grid.getChildren().setAll(items.stream().map(this::card).toList());
    }

    /** Available items sort before the rest. The sort control can put the rest first instead. */
    private Comparator<PhysicalItem> availabilityOrder() {
        Comparator<PhysicalItem> available = Comparator.comparing(
                (PhysicalItem item) -> item.status() == ItemStatus.AVAILABLE ? 0 : 1);
        return NOT_AVAILABLE_FIRST.equals(sort.getValue()) ? available.reversed() : available;
    }

    private VBox card(PhysicalItem item) {
        Label title = new Label(item.title());
        title.getStyleClass().add("item-title");
        title.setWrapText(true);
        Label brand = Ui.muted(item.brandModel() == null ? item.category().dbValue() : item.brandModel());
        Label detail = Ui.muted(Ui.levelText(item.levels()) + "  ·  " + item.condition().dbValue());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Button view = new Button("View");
        view.getStyleClass().add("button-secondary");
        view.setOnAction(event -> shell.show(new ItemDetailView(ctx, shell, item.id()).build()));
        HBox footer = Ui.row(8, Ui.itemBadge(item.status()), gap, view);

        VBox card = new VBox(10, Ui.art(item.category(), 200, 112), title, brand, detail, footer);
        card.getStyleClass().add("card");
        card.setPrefWidth(240);
        return card;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
