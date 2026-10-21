package com.eduaccess.ui;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.MarketplaceCategory;
import com.eduaccess.model.StudentListing;
import com.eduaccess.service.MarketplaceService;
import com.eduaccess.service.ServiceException;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** Dialogs for posting items, editing listings, and submitting borrow requests in the Student Marketplace. */
public final class MarketplaceDialogs {

    private static final List<String> PICKUP_SUGGESTIONS = List.of(
            "Main Campus Student Center", "North Campus Library Lobby", "Science Building Entrance", "Admin Office");

    private MarketplaceDialogs() {
    }

    public static void postListing(AppContext ctx, Runnable onSaved) {
        Dialog<Void> dialog = dialog("Post item to marketplace");
        TextField title = new TextField();
        title.setPromptText("e.g. Scientific Calculator");
        TextArea description = new TextArea();
        description.setPromptText("Describe the item, edition, inclusions, or rules...");
        description.setPrefRowCount(3);
        description.setWrapText(true);

        ComboBox<MarketplaceCategory> category = combo(MarketplaceCategory.values(), MarketplaceCategory::dbValue);
        category.setValue(MarketplaceCategory.CALCULATORS);

        ComboBox<ItemCondition> condition = combo(ItemCondition.values(), ItemCondition::dbValue);
        condition.setValue(ItemCondition.GOOD);

        ComboBox<EducationLevel> level = combo(EducationLevel.values(), EducationLevel::dbValue);
        level.setValue(EducationLevel.COLLEGE);

        TextField dailyFee = new TextField("0.00");
        dailyFee.setPromptText("0.00 for free");

        ComboBox<String> pickup = new ComboBox<>(FXCollections.observableArrayList(PICKUP_SUGGESTIONS));
        pickup.setEditable(true);
        pickup.setValue(PICKUP_SUGGESTIONS.get(0));
        pickup.setMaxWidth(Double.MAX_VALUE);

        Path[] photo = {null};
        Label photoName = Ui.muted("No photo chosen");
        Button choosePhoto = new Button("Choose photo");
        choosePhoto.getStyleClass().add("button-secondary");
        choosePhoto.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose photo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
            File file = chooser.showOpenDialog(null);
            if (file != null) {
                photo[0] = file.toPath();
                photoName.setText(file.getName());
            }
        });

        CheckBox verify18 = new CheckBox("I confirm I am 18 years of age or older and agree to peer lending terms.");
        verify18.setWrapText(true);

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Title", title);
        addRow(grid, row++, "Description", description);
        addRow(grid, row++, "Category", category);
        addRow(grid, row++, "Condition", condition);
        addRow(grid, row++, "Target level", level);
        addRow(grid, row++, "Daily fee (₱)", dailyFee);
        addRow(grid, row++, "Pickup point", pickup);
        addRow(grid, row++, "Photo (optional)", Ui.row(12, choosePhoto, photoName));
        grid.add(verify18, 0, row++, 2, 1);

        Label error = errorLabel();
        grid.add(error, 0, row, 2, 1);

        ButtonType save = saveButton(dialog);
        onSave(dialog, save, error, () -> {
            if (!verify18.isSelected()) {
                throw new ServiceException("You must confirm you are 18+ to post an item.");
            }
            BigDecimal fee = new BigDecimal(dailyFee.getText().strip());
            MarketplaceService.ListingForm form = new MarketplaceService.ListingForm(
                    title.getText(), description.getText(), category.getValue(), condition.getValue(),
                    level.getValue(), fee, pickup.getValue(), photo[0]
            );
            ctx.marketplace.postListing(ctx.session.require(), form);
            Ui.info("\"" + title.getText().strip() + "\" was posted to the Student Marketplace.");
        }, onSaved);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    public static void requestBorrow(AppContext ctx, StudentListing listing, Runnable onSaved) {
        Dialog<Void> dialog = dialog("Request to borrow: " + listing.title());
        TextArea note = new TextArea();
        note.setPromptText("Add a friendly note for the owner about when you need it and when you'll return it...");
        note.setPrefRowCount(4);
        note.setWrapText(true);

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Note to owner", note);
        Label error = errorLabel();
        grid.add(error, 0, row, 2, 1);

        ButtonType save = new ButtonType("Send Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);

        onSave(dialog, save, error, () -> {
            ctx.marketplace.requestBorrow(ctx.session.require(), listing.id(), note.getText());
            Ui.info("Borrow request sent successfully to the item owner.");
        }, onSaved);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    private static Dialog<Void> dialog(String title) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        Ui.style(dialog);
        dialog.getDialogPane().setPadding(new Insets(20, 24, 12, 24));
        return dialog;
    }

    private static ButtonType saveButton(Dialog<Void> dialog) {
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        return save;
    }

    private static void onSave(Dialog<Void> dialog, ButtonType save, Label error, Runnable action, Runnable onSaved) {
        Button button = (Button) dialog.getDialogPane().lookupButton(save);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            error.setText("");
            try {
                action.run();
                onSaved.run();
            } catch (ServiceException e) {
                error.setText(e.getMessage());
                event.consume();
            } catch (NumberFormatException e) {
                error.setText("Please enter a valid numeric daily fee.");
                event.consume();
            } catch (RuntimeException e) {
                e.printStackTrace();
                error.setText("Something went wrong. Please try again.");
                event.consume();
            }
        });
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.setMinWidth(480);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
        Label name = new Label(label);
        name.getStyleClass().add("field-label");
        grid.add(name, 0, row);
        grid.add(control, 1, row);
        if (control instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static Label errorLabel() {
        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        return error;
    }

    private static <T> ComboBox<T> combo(T[] values, Function<T, String> label) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
