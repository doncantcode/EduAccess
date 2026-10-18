package com.eduaccess.ui;

import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.RentalCategory;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.service.RentalService;
import com.eduaccess.service.RentalService.OfferForm;
import com.eduaccess.service.RentalService.RequestForm;
import com.eduaccess.service.ServiceException;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongConsumer;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Modal dialogs for Library Rental Requests: the student's offer, the staff request form and review steps. */
final class RentalDialogs {

    static final List<String> DROP_OFF_POINTS = List.of("Library Counter A", "Library Counter B", "Admin Office");

    private RentalDialogs() {
    }

    // ----- Student: Offer Item for Rent ------------------------------------------------------------------------

    /** Opens the offer form for one request. onSaved runs after the offer is stored. */
    static void offerItem(AppContext ctx, RentalRequest request, Runnable onSaved) {
        Dialog<ButtonType> dialog = dialog("Offer Item for Rent", "For: " + request.title() + "  ·  " + request.purpose());
        Path[] photo = new Path[1];

        TextField name = new TextField();
        name.setPromptText("e.g. Projector");
        TextField brand = new TextField();
        brand.setPromptText("e.g. Epson EB-X51");
        ToggleGroup condition = new ToggleGroup();
        HBox conditions = Ui.segmented(condition, Arrays.asList(ItemCondition.values()), RentalDialogs::conditionText,
                request.minCondition() == ItemCondition.LIKE_NEW ? ItemCondition.LIKE_NEW : ItemCondition.GOOD,
                value -> value.meets(request.minCondition()));
        Label conditionHint = hint(request.minCondition() == ItemCondition.FAIR
                ? "Any working condition is fine."
                : "This request needs " + request.minCondition().dbValue() + " condition or better.");
        TextArea description = new TextArea();
        description.setPromptText("What's included? Cables, case, chargers, anything to know.");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        TextField note = new TextField();
        note.setPromptText("Optional, e.g. \"I can drop it off after 3 PM.\"");
        Label photoName = Ui.muted("No photo selected");
        Button choose = new Button("Choose photo", Icons.UPLOAD.node(16));
        choose.getStyleClass().addAll("button-secondary", "button-small");
        choose.setOnAction(event -> {
            File file = choosePhoto(choose);
            if (file != null) {
                photo[0] = file.toPath();
                photoName.setText(file.getName());
            }
        });
        HBox photoRow = new HBox(12, choose, photoName);
        photoRow.getStyleClass().add("photo-drop");

        CheckBox adult = new CheckBox("I am 18 years old or older. I'll bring my school ID, which staff check at "
                + "hand-over.");
        adult.setWrapText(true);
        adult.setMaxWidth(Double.MAX_VALUE);
        adult.setMinHeight(Region.USE_PREF_SIZE);
        CheckBox agree = new CheckBox("I can bring this item to " + request.dropoffPoint() + " by "
                + Ui.date(request.neededFrom()) + ", and collect it after " + Ui.date(request.neededUntil()) + ".");
        agree.setWrapText(true);
        agree.setMaxWidth(Double.MAX_VALUE);
        agree.setMinHeight(Region.USE_PREF_SIZE);
        Label error = errorLabel();

        GridPane form = formGrid();
        form.add(Ui.labelled("Item name *", name), 0, 0);
        form.add(Ui.labelled("Brand / model", brand), 1, 0);
        form.add(new VBox(6, Ui.fieldLabel("Condition *"), conditions, conditionHint), 0, 1, 2, 1);
        form.add(Ui.labelled("Description", description), 0, 2, 2, 1);
        form.add(Ui.labelled("Photo (optional)", photoRow), 0, 3, 2, 1);
        form.add(Ui.labelled("Note to the library", note), 0, 4, 2, 1);

        Label ageRule = Ui.banner(RentalService.AGE_RULE + " The library rents your item under an agreement, "
                + "so the owner must be an adult.", "info");
        VBox content = new VBox(18, requestSummary(request), ageRule, form, new VBox(10, adult, agree), error);
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);

        ButtonType submit = new ButtonType("Submit offer", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, submit);
        guard(dialog, submit, error, () -> ctx.rentals.offer(ctx.session.require(), request.id(), new OfferForm(
                name.getText(),
                brand.getText(),
                description.getText(),
                condition.getSelectedToggle() == null ? null
                        : (ItemCondition) condition.getSelectedToggle().getUserData(),
                photo[0],
                note.getText(),
                agree.isSelected(),
                adult.isSelected()), LocalDate.now()));
        name.requestFocus();
        dialog.showAndWait().filter(button -> button == submit).ifPresent(button -> onSaved.run());
    }

    /** The request's key facts at the top of the offer dialog. */
    private static Node requestSummary(RentalRequest r) {
        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(10);
        summaryFact(grid, 0, 0, Icons.CALENDAR, "Needed", Ui.dateRange(r.neededFrom(), r.neededUntil())
                + " (" + r.rentalDays() + (r.rentalDays() == 1 ? " day)" : " days)"));
        summaryFact(grid, 1, 0, Icons.TAG, "You earn", RentalRequestsView.feeText(r.rentalFee()));
        summaryFact(grid, 0, 1, Icons.PIN, "Bring it to", r.dropoffPoint());
        summaryFact(grid, 1, 1, Icons.CLOCK, "Offers close", Ui.date(r.offersCloseOn()));
        VBox box = new VBox(grid);
        box.getStyleClass().add("modal-summary");
        return box;
    }

    private static void summaryFact(GridPane grid, int col, int row, Icons icon, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().addAll("eyebrow");
        Label v = new Label(value);
        v.getStyleClass().add("info-value");
        HBox cell = new HBox(10, icon.node(18), new VBox(1, k, v));
        grid.add(cell, col, row);
    }

    // ----- Staff: Create New Rental Request --------------------------------------------------------------------

    /** The staff form for a new request. onSaved receives the new request id. */
    static void createRequest(AppContext ctx, LongConsumer onSaved) {
        Dialog<ButtonType> dialog = dialog("Create New Rental Request",
                "Students see this on the Library Rental Requests board and can offer their own items.");
        LocalDate today = LocalDate.now();

        TextField title = new TextField();
        title.setPromptText("e.g. Portable projector");
        ComboBox<RentalCategory> category = new ComboBox<>();
        category.getItems().setAll(RentalCategory.values());
        category.setValue(RentalCategory.TECH);
        category.setConverter(Converters.coded());
        category.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> quantity = new Spinner<>(1, 20, 1);
        quantity.setEditable(true);
        quantity.setMaxWidth(Double.MAX_VALUE);
        TextField purpose = new TextField();
        purpose.setPromptText("e.g. Science Fair 2026 project demos");
        TextArea description = new TextArea();
        description.setPromptText("What counts as a good fit? Size, specs, accessories...");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        DatePicker from = new DatePicker(today.plusDays(7));
        DatePicker until = new DatePicker(today.plusDays(9));
        DatePicker closes = new DatePicker(today.plusDays(5));
        for (DatePicker picker : List.of(from, until, closes)) {
            picker.setMaxWidth(Double.MAX_VALUE);
            picker.setEditable(false);
        }
        ComboBox<ItemCondition> minimum = new ComboBox<>();
        minimum.getItems().setAll(ItemCondition.values());
        minimum.setValue(ItemCondition.FAIR);
        minimum.setConverter(Converters.coded());
        minimum.setMaxWidth(Double.MAX_VALUE);
        TextField fee = new TextField();
        fee.setPromptText("e.g. 150");
        ComboBox<String> dropoff = new ComboBox<>();
        dropoff.getItems().setAll(DROP_OFF_POINTS);
        dropoff.setValue(DROP_OFF_POINTS.get(0));
        dropoff.setMaxWidth(Double.MAX_VALUE);

        Label preview = new Label();
        preview.getStyleClass().add("banner");
        preview.getStyleClass().add("banner-info");
        preview.setGraphic(Icons.INFO.node(18));
        preview.setWrapText(true);
        preview.setMaxWidth(Double.MAX_VALUE);
        Runnable updatePreview = () -> preview.setText(previewText(today, from.getValue(), until.getValue(),
                closes.getValue(), fee.getText(), quantity.getValue()));
        from.valueProperty().addListener((obs, was, now) -> {
            if (now != null && until.getValue() != null && until.getValue().isBefore(now)) {
                until.setValue(now);
            }
            updatePreview.run();
        });
        until.valueProperty().addListener((obs, was, now) -> updatePreview.run());
        closes.valueProperty().addListener((obs, was, now) -> updatePreview.run());
        fee.textProperty().addListener((obs, was, now) -> updatePreview.run());
        quantity.valueProperty().addListener((obs, was, now) -> updatePreview.run());
        updatePreview.run();

        Label error = errorLabel();
        GridPane form = formGrid();
        form.add(Ui.labelled("What does the library need? *", title), 0, 0);
        form.add(Ui.labelled("Category", category), 1, 0);
        form.add(Ui.labelled("Event or purpose *", purpose), 0, 1);
        form.add(Ui.labelled("How many?", quantity), 1, 1);
        form.add(Ui.labelled("Description *", description), 0, 2, 2, 1);
        form.add(Ui.labelled("Needed from", from), 0, 3);
        form.add(Ui.labelled("Needed until", until), 1, 3);
        form.add(Ui.labelled("Offers close on", closes), 0, 4);
        form.add(Ui.labelled("Minimum condition", minimum), 1, 4);
        form.add(Ui.labelled("Library pays per item (₱) *", fee), 0, 5);
        form.add(Ui.labelled("Students bring items to", dropoff), 1, 5);

        VBox content = new VBox(16, form, preview, error);
        content.setPrefWidth(640);
        dialog.getDialogPane().setContent(content);

        ButtonType post = new ButtonType("Post request", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, post);
        long[] created = new long[1];
        guard(dialog, post, error, () -> created[0] = ctx.rentals.createRequest(ctx.session.require(),
                new RequestForm(title.getText(), description.getText(), purpose.getText(), category.getValue(),
                        quantity.getValue(), minimum.getValue(), closes.getValue(), from.getValue(), until.getValue(),
                        parseFee(fee.getText()), dropoff.getValue()), today));
        title.requestFocus();
        dialog.showAndWait().filter(button -> button == post).ifPresent(button -> onSaved.accept(created[0]));
    }

    /** What students will see, so staff can check the numbers before posting. */
    private static String previewText(LocalDate today, LocalDate from, LocalDate until, LocalDate closes, String fee,
                                      Integer quantity) {
        if (from == null || until == null || closes == null) {
            return "Choose the dates to see a summary.";
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(from, until) + 1);
        String money;
        try {
            BigDecimal value = parseFee(fee);
            money = value.signum() <= 0 ? "no payment set yet" : Ui.money(value) + " paid per item";
        } catch (ServiceException e) {
            money = "an invalid fee";
        }
        long open = ChronoUnit.DAYS.between(today, closes);
        return "Students will see: " + (quantity == null ? 1 : quantity) + " needed, " + days
                + (days == 1 ? " day" : " days") + ", " + money + ". Offers stay open for "
                + Math.max(0, open) + (open == 1 ? " day." : " days.");
    }

    static BigDecimal parseFee(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.strip().replace("₱", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new ServiceException("Enter the fee as a number, for example 150 or 75.50.");
        }
    }

    // ----- Staff: review steps ---------------------------------------------------------------------------------

    static void decline(AppContext ctx, RentalOffer offer, Runnable onDone) {
        Dialog<ButtonType> dialog = dialog("Decline this offer?",
                offer.itemName() + " from " + offer.ownerName());
        TextArea reason = new TextArea();
        reason.setPromptText("Tell the student why, for example \"We need a brighter projector.\"");
        reason.setPrefRowCount(3);
        reason.setWrapText(true);
        Label error = errorLabel();
        VBox content = new VBox(10, Ui.labelled("Reason (the student sees this)", reason), error);
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);
        ButtonType confirm = new ButtonType("Decline offer", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        guard(dialog, confirm, error, () -> ctx.rentals.decline(ctx.session.require(), offer.id(), reason.getText()));
        dialog.showAndWait().filter(button -> button == confirm).ifPresent(button -> onDone.run());
    }

    static void checkIn(AppContext ctx, RentalOffer offer, Runnable onDone) {
        Dialog<ButtonType> dialog = dialog("Check in " + offer.itemName(),
                offer.ownerName() + " is handing the item over at the counter.");
        ToggleGroup condition = new ToggleGroup();
        HBox picker = Ui.segmented(condition, Arrays.asList(ItemCondition.values()), RentalDialogs::conditionText,
                offer.condition(), value -> true);
        CheckBox idChecked = new CheckBox("I checked " + offer.ownerName() + "'s school ID (" + offer.ownerSchoolId()
                + ") and they are 18 or older.");
        idChecked.setWrapText(true);
        idChecked.setMaxWidth(Double.MAX_VALUE);
        idChecked.setMinHeight(Region.USE_PREF_SIZE);
        Label error = errorLabel();
        VBox content = new VBox(12,
                Ui.muted("The student described it as " + offer.condition().dbValue() + ". Record what you see."),
                Ui.labelled("Condition on arrival", picker), idChecked, error);
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        ButtonType confirm = new ButtonType("Check in", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        guard(dialog, confirm, error, () -> ctx.rentals.checkIn(ctx.session.require(), offer.id(),
                (ItemCondition) condition.getSelectedToggle().getUserData(), idChecked.isSelected()));
        dialog.showAndWait().filter(button -> button == confirm).ifPresent(button -> onDone.run());
    }

    static void returnToOwner(AppContext ctx, RentalOffer offer, Runnable onDone) {
        Dialog<ButtonType> dialog = dialog("Return " + offer.itemName() + " to " + offer.ownerName(),
                "Inspect the item with the owner before handing it back.");
        ToggleGroup condition = new ToggleGroup();
        ItemCondition arrived = offer.conditionIn() == null ? offer.condition() : offer.conditionIn();
        HBox picker = Ui.segmented(condition, Arrays.asList(ItemCondition.values()), RentalDialogs::conditionText,
                arrived, value -> true);
        boolean hasFee = offer.agreedFee() != null && offer.agreedFee().signum() > 0;
        CheckBox paid = new CheckBox(hasFee ? "Rental fee of " + Ui.money(offer.agreedFee()) + " paid to the owner"
                : "No rental fee for this request");
        paid.setSelected(!hasFee);
        paid.setDisable(!hasFee);
        TextArea note = new TextArea();
        note.setPromptText("Only if something changed, e.g. \"Small scratch on the lid.\"");
        note.setPrefRowCount(2);
        note.setWrapText(true);
        Label error = errorLabel();
        VBox content = new VBox(14,
                Ui.muted("It arrived in " + arrived.dbValue() + " condition."),
                Ui.labelled("Condition now", picker), paid, Ui.labelled("Damage note", note), error);
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);
        ButtonType confirm = new ButtonType("Return item", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        guard(dialog, confirm, error, () -> ctx.rentals.returnToOwner(ctx.session.require(), offer.id(),
                (ItemCondition) condition.getSelectedToggle().getUserData(), note.getText(), paid.isSelected(),
                LocalDate.now()));
        dialog.showAndWait().filter(button -> button == confirm).ifPresent(button -> onDone.run());
    }

    // ----- Building blocks -------------------------------------------------------------------------------------

    /** A styled dialog with a title and a one-line subtitle in its header. */
    static Dialog<ButtonType> dialog(String title, String subtitle) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        Ui.style(dialog);
        Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");
        Label sub = Ui.muted(subtitle);
        VBox header = new VBox(4, heading, sub);
        header.getStyleClass().add("dialog-header");
        DialogPane pane = dialog.getDialogPane();
        pane.setHeader(header);
        pane.setPadding(Insets.EMPTY);
        return dialog;
    }

    /**
     * Runs {@code work} when the confirm button is pressed. If it throws, the message appears in the dialog and the
     * dialog stays open, so nothing the user typed is lost.
     */
    static void guard(Dialog<ButtonType> dialog, ButtonType confirm, Label error, Runnable work) {
        Button button = (Button) dialog.getDialogPane().lookupButton(confirm);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                work.run();
            } catch (ServiceException e) {
                showError(error, e.getMessage());
                event.consume();
            } catch (RuntimeException e) {
                e.printStackTrace();
                showError(error, "Something went wrong. Please try again.");
                event.consume();
            }
        });
    }

    private static void showError(Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    static Label errorLabel() {
        Label error = Ui.banner("", "danger");
        error.setVisible(false);
        error.setManaged(false);
        return error;
    }

    static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        half.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(half, half);
        return grid;
    }

    static String conditionText(ItemCondition condition) {
        return condition == ItemCondition.LIKE_NEW ? "Like new" : condition.dbValue();
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-hint");
        label.setWrapText(true);
        return label;
    }

    static File choosePhoto(Node anchor) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a photo of the item");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        return chooser.showOpenDialog(anchor.getScene().getWindow());
    }
}
