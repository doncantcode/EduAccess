package com.eduaccess.ui;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemCondition;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.Loan;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.ResourceTab;
import com.eduaccess.model.User;
import com.eduaccess.service.DigitalLibraryService.ResourceForm;
import com.eduaccess.service.InventoryService.DonationForm;
import com.eduaccess.service.ServiceException;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/** Admin-only dialogs: add a physical item, add a digital resource, and record a borrow at the counter. */
final class AdminForms {

    private static final List<String> PICKUP_POINTS = List.of("Library Counter A", "Library Counter B", "Admin Office");

    private AdminForms() {
    }

    static void addPhysicalItem(AppContext ctx, Runnable onSaved) {
        Dialog<Void> dialog = dialog("Add physical item");
        TextField title = new TextField();
        title.setPromptText("e.g. Scientific Calculator");
        TextField brand = new TextField();
        brand.setPromptText("e.g. Casio fx-991EX");
        ComboBox<ItemCategory> category = combo(ItemCategory.values(), ItemCategory::dbValue);
        category.setValue(ItemCategory.CALCULATORS);
        ComboBox<ItemCondition> condition = combo(ItemCondition.values(), ItemCondition::dbValue);
        condition.setValue(ItemCondition.GOOD);
        List<CheckBox> levels = new ArrayList<>();
        HBox levelBox = new HBox(16);
        for (EducationLevel level : EducationLevel.values()) {
            CheckBox check = new CheckBox(level.dbValue());
            check.setSelected(level == EducationLevel.SHS);
            levels.add(check);
            levelBox.getChildren().add(check);
        }
        ComboBox<String> pickup = new ComboBox<>(FXCollections.observableArrayList(PICKUP_POINTS));
        pickup.setValue(PICKUP_POINTS.get(0));
        pickup.setMaxWidth(Double.MAX_VALUE);
        Path[] photo = {null};
        Label photoName = Ui.muted("No photo chosen");
        Button choosePhoto = new Button("Choose photo");
        choosePhoto.getStyleClass().add("button-secondary");
        choosePhoto.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a photo");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
            File file = chooser.showOpenDialog(null);
            if (file != null) {
                photo[0] = file.toPath();
                photoName.setText(file.getName());
            }
        });

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Item name", title);
        addRow(grid, row++, "Brand / model", brand);
        addRow(grid, row++, "Category", category);
        addRow(grid, row++, "Condition", condition);
        addRow(grid, row++, "Recommended for", levelBox);
        addRow(grid, row++, "Pickup point", pickup);
        addRow(grid, row++, "Photo (optional)", Ui.row(12, choosePhoto, photoName));
        Label error = errorLabel();
        grid.add(error, 0, row, 2, 1);

        ButtonType save = saveButton(dialog);
        onSave(dialog, save, error, () -> {
            List<EducationLevel> chosen = new ArrayList<>();
            for (int i = 0; i < EducationLevel.values().length; i++) {
                if (levels.get(i).isSelected()) {
                    chosen.add(EducationLevel.values()[i]);
                }
            }
            DonationForm form = new DonationForm(title.getText(), brand.getText(), category.getValue(),
                    condition.getValue(), pickup.getValue(), chosen, GivingType.DONATE, photo[0]);
            ctx.inventory.registerLibraryItem(ctx.session.require(), form);
            Ui.info("\"" + title.getText().strip() + "\" was added and is available to borrow.");
        }, onSaved);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    static void addDigitalResource(AppContext ctx, Runnable onSaved) {
        Dialog<Void> dialog = dialog("Add digital resource");
        TextField title = new TextField();
        title.setPromptText("e.g. Grade 8 Science: Cells");
        TextField subject = new TextField();
        subject.setPromptText("e.g. Science");
        TextField source = new TextField();
        source.setPromptText("Where the content came from");
        TextField license = new TextField();
        license.setPromptText("e.g. CC BY 4.0 or Public domain");
        ComboBox<ResourceTab> tab = combo(ResourceTab.values(), ResourceTab::dbValue);
        tab.setValue(ResourceTab.K12_MODULE_HUB);
        ComboBox<EducationLevel> level = combo(EducationLevel.values(), EducationLevel::dbValue);
        level.setValue(EducationLevel.ELEMENTARY);
        tab.valueProperty().addListener((obs, was, now) -> level.setValue(
                now == ResourceTab.COLLEGE_REPOSITORY ? EducationLevel.COLLEGE
                        : level.getValue() == EducationLevel.COLLEGE ? EducationLevel.ELEMENTARY : level.getValue()));

        Path[] pdf = {null};
        Label pdfName = Ui.muted("No PDF chosen");
        Button choosePdf = new Button("Choose PDF");
        choosePdf.getStyleClass().add("button-secondary");
        choosePdf.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a PDF");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File file = chooser.showOpenDialog(null);
            if (file != null) {
                pdf[0] = file.toPath();
                pdfName.setText(file.getName());
            }
        });

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Title", title);
        addRow(grid, row++, "Subject", subject);
        addRow(grid, row++, "Repository", tab);
        addRow(grid, row++, "Education level", level);
        addRow(grid, row++, "Source", source);
        addRow(grid, row++, "License", license);
        addRow(grid, row++, "PDF file", Ui.row(12, choosePdf, pdfName));
        Label error = errorLabel();
        grid.add(error, 0, row, 2, 1);

        ButtonType save = saveButton(dialog);
        onSave(dialog, save, error, () -> {
            ResourceForm form = new ResourceForm(title.getText(), subject.getText(), tab.getValue(),
                    level.getValue(), source.getText(), license.getText(), pdf[0]);
            ctx.library.addResource(ctx.session.require(), form);
            Ui.info("\"" + title.getText().strip() + "\" was added to the library.");
        }, onSaved);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    static void recordBorrow(AppContext ctx, Runnable onSaved) {
        Dialog<Void> dialog = dialog("Record a borrow");
        TextField studentId = new TextField();
        studentId.setPromptText("Student school ID");
        TextField itemId = new TextField();
        itemId.setPromptText("Item ID number");

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Student school ID", studentId);
        addRow(grid, row++, "Item ID", itemId);
        Label error = errorLabel();
        grid.add(error, 0, row, 2, 1);

        ButtonType save = saveButton(dialog);
        onSave(dialog, save, error, () -> {
            User student = ctx.auth.findStudent(studentId.getText());
            long id = Long.parseLong(itemId.getText().strip());
            ctx.loans.borrow(student, id, 14, LocalDate.now());
            Ui.info("Borrow successfully recorded for " + student.fullName() + ".");
        }, onSaved);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    static void resetStudentPassword(AppContext ctx, Runnable onSaved) {
        Dialog<Void> dialog = dialog("Reset student password");
        TextField studentId = new TextField();
        studentId.setPromptText("School ID of the student");
        TextField newPassword = new TextField();
        newPassword.setPromptText("New password (at least 8 chars)");

        GridPane grid = grid();
        int row = 0;
        addRow(grid, row++, "Student school ID", studentId);
        addRow(grid, row++, "New password", newPassword);
        Label error = errorLabel();
        grid.add(error, 0, row, 2, 1);

        ButtonType save = saveButton(dialog);
        onSave(dialog, save, error, () -> {
            User student = ctx.auth.findStudent(studentId.getText());
            String pwd = newPassword.getText();
            if (pwd == null || pwd.length() < 8) {
                throw new ServiceException("Use a password of at least 8 characters.");
            }
            ctx.auth.resetPassword(student.id(), pwd);
            Ui.info("Password successfully reset for " + student.fullName() + ".");
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

    /**
     * Keeps the dialog open when the form is invalid. The message appears in the dialog. On success the save
     * action runs, the dialog closes, and {@code onSaved} refreshes the screen behind it.
     */
    private static void onSave(Dialog<Void> dialog, ButtonType save, Label error, Runnable action,
                               Runnable onSaved) {
        Button button = (Button) dialog.getDialogPane().lookupButton(save);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            error.setText("");
            try {
                action.run();
                onSaved.run();
            } catch (ServiceException e) {
                error.setText(e.getMessage());
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
