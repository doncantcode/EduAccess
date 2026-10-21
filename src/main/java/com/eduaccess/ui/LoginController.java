package com.eduaccess.ui;

import com.eduaccess.model.User;
import com.eduaccess.service.LoginRole;
import com.eduaccess.service.ServiceException;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

/**
 * Controller for the Login screen (login.fxml). It checks the school ID and password through
 * {@link com.eduaccess.service.AuthService} and then hands control back to the application.
 */
public final class LoginController {

    @FXML
    private TextField schoolId;
    @FXML
    private PasswordField password;
    @FXML
    private TextField visiblePassword;
    @FXML
    private ToggleButton showPassword;
    @FXML
    private ToggleGroup roleGroup;
    @FXML
    private Label errorText;

    private final AppContext ctx;
    private final Runnable onSignedIn;
    private final Runnable onRegister;

    public LoginController(AppContext ctx, Runnable onSignedIn, Runnable onRegister) {
        this.ctx = ctx;
        this.onSignedIn = onSignedIn;
        this.onRegister = onRegister;
    }

    /** Called by FXMLLoader after the fields are injected. */
    @FXML
    void initialize() {
        visiblePassword.textProperty().bindBidirectional(password.textProperty());
        showPassword.selectedProperty().addListener((obs, wasShown, shown) -> {
            visiblePassword.setVisible(shown);
            visiblePassword.setManaged(shown);
            password.setVisible(!shown);
            password.setManaged(!shown);
        });
    }

    @FXML
    private void handleSignIn() {
        errorText.setText("");
        try {
            User user = ctx.auth.login(schoolId.getText(), password.getText().toCharArray(), selectedRole());
            ctx.session.signIn(user);
            onSignedIn.run();
        } catch (ServiceException e) {
            errorText.setText(e.getMessage());
        }
    }

    @FXML
    private void handleRegister() {
        onRegister.run();
    }

    @FXML
    private void handleForgotPassword() {
        ForgotPasswordDialog.show(ctx, schoolId.getText());
    }

    /** The radio buttons carry the LoginRole name as their user data. */
    private LoginRole selectedRole() {
        return LoginRole.valueOf((String) roleGroup.getSelectedToggle().getUserData());
    }
}
