package com.eduaccess.ui;

import com.eduaccess.model.PasswordResetRequest;
import com.eduaccess.model.User;
import com.eduaccess.service.ServiceException;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Admin screen to review and approve/reject student password reset requests. */
public final class PasswordResetsView {

    private final AppContext ctx;
    private final AppShell shell;

    public PasswordResetsView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    public Node build() {
        VBox page = new VBox(20);
        page.getChildren().add(Ui.pageHeader("Password Reset Requests", "Review student password reset requests and set new passwords."));

        List<PasswordResetRequest> requests;
        try {
            User admin = ctx.session.require();
            requests = ctx.auth.pendingPasswordResetRequests(admin);
        } catch (ServiceException e) {
            page.getChildren().add(Ui.banner(e.getMessage(), "danger"));
            return page;
        }

        if (requests.isEmpty()) {
            page.getChildren().add(Ui.card(new Label("No pending password reset requests.")));
            return page;
        }

        VBox listCard = new VBox(16);
        for (PasswordResetRequest req : requests) {
            VBox reqBox = new VBox(10);
            reqBox.getStyleClass().add("card-flat");
            reqBox.setPadding(new Insets(16));

            Label title = new Label(req.fullName() + " (" + req.schoolId() + ")");
            title.getStyleClass().add("heading");
            Label date = new Label("Requested at: " + req.requestedAt());
            date.getStyleClass().add("muted");

            PasswordField newPassField = new PasswordField();
            newPassField.setPromptText("New password (at least 8 chars)");
            newPassField.setMaxWidth(300);

            Label error = new Label();
            error.getStyleClass().add("error-text");

            Button approveBtn = new Button("Approve & Reset Password");
            approveBtn.getStyleClass().add("button-primary");
            approveBtn.setOnAction(e -> {
                error.setText("");
                try {
                    User admin = ctx.session.require();
                    ctx.auth.approvePasswordResetRequest(admin, req.requestId(), newPassField.getText());
                    shell.go(Screen.PASSWORD_RESETS, "Password successfully reset for " + req.fullName() + ".");
                } catch (ServiceException ex) {
                    error.setText(ex.getMessage());
                }
            });

            Button rejectBtn = new Button("Reject");
            rejectBtn.getStyleClass().add("button-secondary");
            rejectBtn.setOnAction(e -> {
                try {
                    User admin = ctx.session.require();
                    ctx.auth.rejectPasswordResetRequest(admin, req.requestId());
                    shell.go(Screen.PASSWORD_RESETS, "Password reset request rejected.");
                } catch (ServiceException ex) {
                    error.setText(ex.getMessage());
                }
            });

            HBox actions = new HBox(12, newPassField, approveBtn, rejectBtn);
            actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            reqBox.getChildren().addAll(title, date, actions, error);
            listCard.getChildren().add(reqBox);
        }

        page.getChildren().add(Ui.card(listCard));
        return page;
    }
}
