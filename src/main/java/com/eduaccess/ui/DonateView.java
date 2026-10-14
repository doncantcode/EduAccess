package com.eduaccess.ui;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.User;
import com.eduaccess.service.InventoryService.DonationForm;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Screen 6: the donate-or-lend form with a live listing preview and drop-off instructions. */
final class DonateView {

    private static final List<String> PICKUP_POINTS = List.of("Library Counter A", "Library Counter B", "Admin Office");

    private final AppContext ctx;
    private final AppShell shell;
    private final ToggleGroup typeGroup = new ToggleGroup();
    private final TextField name = new TextField();
    private final TextField brand = new TextField();
    private final ToggleGroup conditionGroup = new ToggleGroup();
    private final Map<EducationLevel, CheckBox> levelChecks = new EnumMap<>(EducationLevel.class);
    private final ToggleGroup givingGroup = new ToggleGroup();
    private final ComboBox<String> dropOff = new ComboBox<>();
    private final Label photoLabel = Ui.muted("No photo selected");
    private final VBox preview = new VBox(10);
    private Path photo;

    DonateView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        Label title = Ui.heading("Donate or lend an item");
        Label subtitle = Ui.muted("Help a younger student learn. The library checks each item before it is listed.");

        VBox form = Ui.card(typeSection(), detailsSection(), actions());
        HBox.setHgrow(form, Priority.ALWAYS);
        VBox previewCard = Ui.card(Ui.section("Listing preview"),
                Ui.muted("This is how borrowers will see your item."), preview);
        previewCard.setPrefWidth(340);
        previewCard.setMinWidth(320);

        configureControls();
        refreshPreview();
        return new VBox(14, new VBox(4, title, subtitle), new HBox(20, form, previewCard));
    }

    private VBox typeSection() {
        HBox types = new HBox(10);
        types.getChildren().addAll(
                typeButton("Book", ItemCategory.TEXTBOOKS),
                typeButton("Calculator", ItemCategory.CALCULATORS),
                typeButton("Laptop / Tablet", ItemCategory.LAPTOPS_TABLETS),
                typeButton("Study kit", ItemCategory.LAB_STUDY_KITS));
        return new VBox(10, Ui.section("1. What are you donating?"), types);
    }

    private ToggleButton typeButton(String text, ItemCategory category) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("toggle-card");
        button.setToggleGroup(typeGroup);
        button.setUserData(category);
        button.setSelected(category == ItemCategory.CALCULATORS);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private VBox detailsSection() {
        name.setPromptText("e.g. Scientific Calculator");
        brand.setPromptText("e.g. Casio fx-991EX");

        HBox conditions = new HBox(14);
        for (ItemCondition condition : ItemCondition.values()) {
            RadioButton radio = new RadioButton(condition == ItemCondition.LIKE_NEW ? "Like new" : condition.dbValue());
            radio.setToggleGroup(conditionGroup);
            radio.setUserData(condition);
            radio.setSelected(condition == ItemCondition.GOOD);
            conditions.getChildren().add(radio);
        }

        HBox levels = new HBox(14);
        for (EducationLevel level : EducationLevel.values()) {
            CheckBox check = new CheckBox(level.dbValue());
            check.setSelected(level == EducationLevel.SHS || level == EducationLevel.COLLEGE);
            check.selectedProperty().addListener((obs, was, now) -> refreshPreview());
            levelChecks.put(level, check);
            levels.getChildren().add(check);
        }

        Button upload = new Button("Upload photo");
        upload.getStyleClass().add("button-secondary");
        upload.setOnAction(event -> choosePhoto(upload));
        HBox photoRow = Ui.row(12, upload, photoLabel);

        for (GivingType giving : GivingType.values()) {
            RadioButton radio = new RadioButton(giving == GivingType.DONATE ? "Donate" : "Lend (get back later)");
            radio.setToggleGroup(givingGroup);
            radio.setUserData(giving);
            radio.setSelected(giving == GivingType.DONATE);
        }

        return new VBox(12,
                Ui.section("2. Item details"),
                Ui.row(12, labelled("Item name", name), labelled("Brand / Model", brand)),
                labelled("Condition", conditions),
                labelled("Recommended for (choose one or more)", levels),
                labelled("Photo", photoRow),
                labelled("Type of giving", givingButtons()),
                labelled("Drop-off point", dropOff));
    }

    private HBox givingButtons() {
        HBox box = new HBox(14);
        givingGroup.getToggles().forEach(toggle -> box.getChildren().add((RadioButton) toggle));
        return box;
    }

    private VBox labelled(String text, Node control) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return new VBox(6, label, control);
    }

    private HBox actions() {
        Button submit = new Button("Submit donation");
        submit.getStyleClass().add("button-primary");
        submit.setOnAction(event -> submit());
        Button reset = new Button("Clear form");
        reset.getStyleClass().add("button-secondary");
        reset.setOnAction(event -> resetForm());
        return Ui.row(10, reset, submit);
    }

    private void configureControls() {
        dropOff.getItems().setAll(PICKUP_POINTS);
        dropOff.setValue(PICKUP_POINTS.get(0));
        dropOff.setMaxWidth(Double.MAX_VALUE);
        dropOff.valueProperty().addListener((obs, was, now) -> refreshPreview());
        name.textProperty().addListener((obs, was, now) -> refreshPreview());
        brand.textProperty().addListener((obs, was, now) -> refreshPreview());
        typeGroup.selectedToggleProperty().addListener((obs, was, now) -> refreshPreview());
        conditionGroup.selectedToggleProperty().addListener((obs, was, now) -> refreshPreview());
        givingGroup.selectedToggleProperty().addListener((obs, was, now) -> refreshPreview());
    }

    private void refreshPreview() {
        ItemCategory category = selectedCategory();
        List<EducationLevel> levels = selectedLevels();
        Label itemName = new Label(name.getText().isBlank() ? "Item name" : name.getText().strip());
        itemName.getStyleClass().add("item-title");
        itemName.setWrapText(true);
        Label model = Ui.muted(brand.getText().isBlank() ? "Brand / model" : brand.getText().strip());
        String condition = selectedCondition() == null ? "Condition" : selectedCondition().dbValue();
        Label detail = Ui.muted((levels.isEmpty() ? "Recommended level" : Ui.levelText(levels))
                + "  ·  " + condition);
        Label drop = Ui.muted("Drop-off: " + dropOff.getValue() + ". Bring the item and your school ID.");
        preview.getChildren().setAll(
                Ui.art(category == null ? ItemCategory.CALCULATORS : category, 300, 160),
                itemName, model, detail,
                Ui.badge("Pending library check", "pending"),
                drop);
    }

    private void choosePhoto(Node anchor) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo of the item");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(anchor.getScene().getWindow());
        if (file != null) {
            photo = file.toPath();
            photoLabel.setText(file.getName());
        }
    }

    private void submit() {
        User donor = ctx.session.require();
        DonationForm form = new DonationForm(
                name.getText(),
                brand.getText(),
                selectedCategory(),
                selectedCondition(),
                dropOff.getValue(),
                selectedLevels(),
                selectedGiving(),
                photo);
        Ui.run(() -> {
            ctx.inventory.submitDonation(donor, form);
            Ui.info("Thank you! Your donation is waiting for the library check. Bring the item and your school ID to "
                    + form.pickupPoint() + ".");
            resetForm();
            shell.go(Screen.DONATE);
        });
    }

    private void resetForm() {
        name.clear();
        brand.clear();
        photo = null;
        photoLabel.setText("No photo selected");
        typeGroup.selectToggle(typeGroup.getToggles().get(1));
        conditionGroup.selectToggle(conditionGroup.getToggles().get(1));
        givingGroup.selectToggle(givingGroup.getToggles().get(0));
        levelChecks.forEach((level, check) ->
                check.setSelected(level == EducationLevel.SHS || level == EducationLevel.COLLEGE));
        dropOff.setValue(PICKUP_POINTS.get(0));
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

    private GivingType selectedGiving() {
        return givingGroup.getSelectedToggle() == null ? null
                : (GivingType) givingGroup.getSelectedToggle().getUserData();
    }

    private List<EducationLevel> selectedLevels() {
        List<EducationLevel> levels = new ArrayList<>();
        levelChecks.forEach((level, check) -> {
            if (check.isSelected()) {
                levels.add(level);
            }
        });
        return levels;
    }
}
