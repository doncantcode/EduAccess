package com.eduaccess.ui;

import com.eduaccess.model.Catalog;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.User;
import com.eduaccess.service.InventoryService.DonationForm;
import com.eduaccess.service.ServiceException;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

/**
 * Donate an Item: a guided, top-to-bottom form for permanent gifts, with a live listing preview on the side.
 * Lending to the library for a short time is a separate workflow, Library Rental Requests.
 */
final class DonateView {

    private static final List<String> DROP_OFF_POINTS = RentalDialogs.DROP_OFF_POINTS;

    private final AppContext ctx;
    private final AppShell shell;
    private final ToggleGroup typeGroup = new ToggleGroup();
    private final TextField name = new TextField();
    private final TextField brand = new TextField();
    private final ToggleGroup conditionGroup = new ToggleGroup();
    private final Map<EducationLevel, ToggleButton> levelChips = new EnumMap<>(EducationLevel.class);
    private final ComboBox<String> dropOff = new ComboBox<>();
    private final Label photoLabel = Ui.muted("No photo selected");
    private final Label brandLabel = Ui.fieldLabel("Brand / model");
    private final Label conditionHint = new Label();
    private final Label error = RentalDialogs.errorLabel();
    private final VBox preview = new VBox(12);
    private final VBox whereItGoes = new VBox(6);
    private Path photo;

    DonateView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        HBox header = Ui.pageHeader("Donate an Item",
                "Give a book or study tool to the library for good. Staff check every item before it is listed.");

        Hyperlink rentLink = new Hyperlink("See Library Rental Requests");
        rentLink.getStyleClass().add("link");
        rentLink.setOnAction(event -> shell.go(Screen.RENTAL_REQUESTS));
        Label note = new Label("Donations are permanent gifts. Want the library to borrow something and give it back?");
        note.setWrapText(true);
        HBox info = Ui.row(6, Icons.INFO.node(18), note, rentLink);
        info.getStyleClass().addAll("banner", "banner-info");

        VBox form = Ui.card(
                step(1, "What are you donating?", typeTiles()),
                divider(),
                step(2, "Tell us about it", details()),
                divider(),
                step(3, "Add a photo", photoPicker()),
                divider(),
                step(4, "Drop-off", dropOffPicker()),
                error,
                actions());
        form.setSpacing(20);
        HBox.setHgrow(form, Priority.ALWAYS);

        VBox side = sidePanel();

        configureControls();
        refreshPreview();
        return new VBox(18, header, info, new HBox(22, form, side));
    }

    // ----- Form sections ---------------------------------------------------------------------------------------

    private Node step(int number, String title, Node body) {
        Label badge = new Label(String.valueOf(number));
        badge.getStyleClass().add("step-number");
        Label heading = new Label(title);
        heading.getStyleClass().add("step-title");
        return new VBox(14, Ui.row(10, badge, heading), body);
    }

    private Node typeTiles() {
        TilePane tiles = new TilePane(12, 12);
        tiles.setPrefColumns(4);
        tiles.setPrefTileWidth(150);
        tiles.getChildren().addAll(
                typeTile("Book", Icons.BOOK, ItemCategory.TEXTBOOKS),
                typeTile("Calculator", Icons.CALCULATOR, ItemCategory.CALCULATORS),
                typeTile("Laptop / Tablet", Icons.LAPTOP, ItemCategory.LAPTOPS_TABLETS),
                typeTile("Study Kit", Icons.FLASK, ItemCategory.LAB_STUDY_KITS));
        typeGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true);
            }
        });
        return tiles;
    }

    private ToggleButton typeTile(String text, Icons icon, ItemCategory category) {
        ToggleButton tile = new ToggleButton(text, icon.node(28));
        tile.getStyleClass().add("toggle-card");
        tile.setToggleGroup(typeGroup);
        tile.setUserData(category);
        tile.setSelected(category == ItemCategory.TEXTBOOKS);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setPrefHeight(92);
        return tile;
    }

    private Node details() {
        name.setPromptText("e.g. General Chemistry 1 Reviewer");
        HBox conditions = Ui.segmented(conditionGroup, Arrays.asList(ItemCondition.values()),
                RentalDialogs::conditionText, ItemCondition.GOOD, value -> true);
        conditionHint.getStyleClass().add("field-hint");

        HBox levels = new HBox(8);
        for (EducationLevel level : EducationLevel.values()) {
            ToggleButton chip = new ToggleButton(level == EducationLevel.JHS ? "Junior High"
                    : level == EducationLevel.SHS ? "Senior High" : level.dbValue());
            chip.getStyleClass().add("chip");
            chip.setSelected(level == EducationLevel.SHS || level == EducationLevel.COLLEGE);
            chip.selectedProperty().addListener((obs, was, now) -> refreshPreview());
            levelChips.put(level, chip);
            levels.getChildren().add(chip);
        }

        GridPane grid = RentalDialogs.formGrid();
        grid.add(Ui.labelled("Item name *", name), 0, 0);
        grid.add(new VBox(6, brandLabel, brand), 1, 0);
        grid.add(new VBox(6, Ui.fieldLabel("Condition *"), conditions, conditionHint), 0, 1, 2, 1);
        grid.add(new VBox(6, Ui.fieldLabel("Recommended for * (choose one or more)"), levels), 0, 2, 2, 1);
        return grid;
    }

    private Node photoPicker() {
        Button upload = new Button("Choose photo", Icons.UPLOAD.node(16));
        upload.getStyleClass().addAll("button-secondary", "button-small");
        upload.setOnAction(event -> {
            File file = RentalDialogs.choosePhoto(upload);
            if (file != null) {
                photo = file.toPath();
                photoLabel.setText(file.getName());
            }
        });
        Label hint = new Label("Optional. A clear JPG or PNG helps students recognise the item.");
        hint.getStyleClass().add("field-hint");
        VBox text = new VBox(2, photoLabel, hint);
        HBox box = new HBox(14, Icons.UPLOAD.node(26), text, Ui.grow(), upload);
        box.getStyleClass().add("photo-drop");
        return box;
    }

    private Node dropOffPicker() {
        dropOff.getItems().setAll(DROP_OFF_POINTS);
        dropOff.setValue(DROP_OFF_POINTS.get(0));
        dropOff.setPrefWidth(280);
        Label hint = new Label("Bring the item and your school ID. Staff check it with you before it is listed.");
        hint.getStyleClass().add("field-hint");
        hint.setWrapText(true);
        return new VBox(6, dropOff, hint);
    }

    private Node actions() {
        Button submit = new Button("Submit donation", Icons.HEART.node(16));
        submit.getStyleClass().add("button-primary");
        submit.setDefaultButton(true);
        submit.setOnAction(event -> submit());
        Button reset = new Button("Clear form");
        reset.getStyleClass().add("button-secondary");
        reset.setOnAction(event -> resetForm());
        return Ui.row(10, Ui.grow(), reset, submit);
    }

    private static Node divider() {
        Region line = new Region();
        line.getStyleClass().add("divider");
        return line;
    }

    // ----- Live preview ----------------------------------------------------------------------------------------

    private VBox sidePanel() {
        VBox previewCard = Ui.card(Ui.section("Live listing preview"),
                Ui.muted("This is how students will see your item."), preview);
        previewCard.getStyleClass().add("preview-card");

        VBox whereCard = Ui.card(Ui.section("Where it will appear"), whereItGoes);

        VBox next = new VBox(10,
                nextStep("1", "Bring the item to the drop-off point."),
                nextStep("2", "Staff inspect it and approve the listing."),
                nextStep("3", "Students can borrow it straight away."));
        VBox nextCard = Ui.card(Ui.section("What happens next"), next);

        VBox side = new VBox(16, previewCard, whereCard, nextCard);
        side.getStyleClass().add("sticky-panel");
        side.setPrefWidth(340);
        side.setMinWidth(320);
        side.setMaxWidth(360);
        return side;
    }

    private static Node nextStep(String number, String text) {
        Label badge = new Label(number);
        badge.getStyleClass().add("step-number");
        Label label = new Label(text);
        label.setWrapText(true);
        return Ui.row(10, badge, label);
    }

    private void configureControls() {
        dropOff.valueProperty().addListener((obs, was, now) -> refreshPreview());
        name.textProperty().addListener((obs, was, now) -> refreshPreview());
        brand.textProperty().addListener((obs, was, now) -> refreshPreview());
        typeGroup.selectedToggleProperty().addListener((obs, was, now) -> refreshPreview());
        conditionGroup.selectedToggleProperty().addListener((obs, was, now) -> refreshPreview());
    }

    private void refreshPreview() {
        ItemCategory category = selectedCategory() == null ? ItemCategory.TEXTBOOKS : selectedCategory();
        boolean book = category == ItemCategory.TEXTBOOKS;
        brandLabel.setText(book ? "Author / edition" : "Brand / model");
        brand.setPromptText(book ? "e.g. 2nd edition, 2023" : "e.g. Casio fx-991EX");

        ItemCondition condition = selectedCondition();
        conditionHint.setText(switch (condition == null ? ItemCondition.GOOD : condition) {
            case LIKE_NEW -> "Barely used. No marks, all parts included.";
            case GOOD -> "Used but works well. Small signs of wear.";
            case FAIR -> "Works, with visible wear or missing extras.";
        });

        List<EducationLevel> levels = selectedLevels();
        Label pill = new Label(category.dbValue().toUpperCase());
        pill.getStyleClass().add("category-pill");
        Label itemName = new Label(name.getText().isBlank() ? "Item name" : name.getText().strip());
        itemName.getStyleClass().add("item-title");
        itemName.setWrapText(true);
        Label model = Ui.muted(brand.getText().isBlank() ? (book ? "Author / edition" : "Brand / model")
                : brand.getText().strip());
        String conditionText = condition == null ? "Condition" : RentalDialogs.conditionText(condition);
        Label detail = Ui.muted((levels.isEmpty() ? "Recommended level" : Ui.levelText(levels))
                + "  ·  " + conditionText);
        detail.getStyleClass().add("small");
        preview.getChildren().setAll(
                Ui.art(category, 296, 150), pill, itemName, model, detail,
                Ui.badge("Pending library check", "pending"));

        boolean library = category.catalog() == Catalog.LIBRARY;
        Label tab = new Label(library ? "Physical Library" : "Physical Hub");
        tab.getStyleClass().add("item-title");
        Label why = Ui.muted(library
                ? "Books and reading material are listed on the library shelves."
                : "Equipment and study tools are listed in the Physical Hub.");
        why.getStyleClass().add("small");
        whereItGoes.getChildren().setAll(Ui.row(10, (library ? Icons.BOOK : Icons.BOX).node(20), tab), why,
                Ui.muted("Drop-off: " + dropOff.getValue()));
    }

    // ----- Submit ----------------------------------------------------------------------------------------------

    private void submit() {
        User donor = ctx.session.require();
        DonationForm form = new DonationForm(
                name.getText(),
                brand.getText(),
                selectedCategory(),
                selectedCondition(),
                dropOff.getValue(),
                selectedLevels(),
                GivingType.DONATE,
                photo);
        try {
            ctx.inventory.submitDonation(donor, form);
            shell.go(Screen.DONATE, "Thank you! Your donation is waiting for the library check. Bring \""
                    + form.title().strip() + "\" and your school ID to " + form.pickupPoint() + ".");
        } catch (ServiceException e) {
            error.setText(e.getMessage());
            error.setVisible(true);
            error.setManaged(true);
            name.getStyleClass().remove("field-invalid");
            if (form.title() == null || form.title().isBlank()) {
                name.getStyleClass().add("field-invalid");
                name.requestFocus();
            }
        }
    }

    private void resetForm() {
        name.clear();
        brand.clear();
        photo = null;
        photoLabel.setText("No photo selected");
        typeGroup.getToggles().get(0).setSelected(true);
        conditionGroup.getToggles().get(1).setSelected(true);
        levelChips.forEach((level, chip) ->
                chip.setSelected(level == EducationLevel.SHS || level == EducationLevel.COLLEGE));
        dropOff.setValue(DROP_OFF_POINTS.get(0));
        error.setVisible(false);
        error.setManaged(false);
        name.getStyleClass().remove("field-invalid");
        refreshPreview();
    }

    private ItemCategory selectedCategory() {
        return typeGroup.getSelectedToggle() == null ? null
                : (ItemCategory) typeGroup.getSelectedToggle().getUserData();
    }

    private ItemCondition selectedCondition() {
        return conditionGroup.getSelectedToggle() == null ? null
                : (ItemCondition) conditionGroup.getSelectedToggle().getUserData();
    }

    private List<EducationLevel> selectedLevels() {
        List<EducationLevel> levels = new ArrayList<>();
        levelChips.forEach((level, chip) -> {
            if (chip.isSelected()) {
                levels.add(level);
            }
        });
        return levels;
    }
}
