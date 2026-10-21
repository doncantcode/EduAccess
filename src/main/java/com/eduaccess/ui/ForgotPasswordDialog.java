package com.eduaccess.ui;

import com.eduaccess.service.ServiceException;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** Dialog allowing a student to request a password reset from the library administrator. */
public final class ForgotPasswordDialog {

    private ForgotPasswordDialog() {
    }

    public static void show(AppContext ctx, String prefilledStudentId) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Request Password Reset");
        dialog.setHeaderText(null);
        Ui.style(dialog);

        TextField studentIdField = new TextField(prefilledStudentId != null ? prefilledStudentId : "");
        studentIdField.setPromptText("e.g. 2026-SHS-0001");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.setMinWidth(420);

        int row = 0;
        grid.add(new Label("Enter your school ID to request a password reset.\nAn administrator will review and reset your password."), 0, row++, 2, 1);
        grid.add(fieldLabel("School ID"), 0, row);
        grid.add(studentIdField, 1, row++);

        Label error = new Label();
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        grid.add(error, 0, row, 2, 1);

        ButtonType submitBtn = new ButtonType("Submit Request", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submitBtn);

        Button button = (Button) dialog.getDialogPane().lookupButton(submitBtn);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            error.setText("");
            try {
                ctx.auth.requestPasswordReset(studentIdField.getText());
                Ui.info("Password reset request submitted successfully. Please wait for an administrator to review your request.");
            } catch (ServiceException e) {
                error.setText(e.getMessage());
                event.consume();
            } catch (RuntimeException e) {
                e.printStackTrace();
                error.setText("Something went wrong. Please try again.");
                event.consume();
            }
        });

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait();
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }
}
