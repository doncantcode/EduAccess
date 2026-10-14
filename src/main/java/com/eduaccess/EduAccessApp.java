package com.eduaccess;

import com.eduaccess.db.DatabaseInitializer;
import com.eduaccess.db.DatabaseManager;
import com.eduaccess.ui.AppContext;
import com.eduaccess.ui.AppShell;
import com.eduaccess.ui.LoginController;
import com.eduaccess.ui.RegistrationView;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * JavaFX entry point. On start it creates or opens eduaccess.db, then shows the Login screen (Screen 1, FXML)
 * on the primary Stage.
 *
 * <p>The demo data loads when the users table is empty. Turn it off with {@code -Deduaccess.demoSeed=false}.
 */
public class EduAccessApp extends Application {

    private static final Path DB_FILE = Path.of("data", "eduaccess.db");
    private static final String LOGIN_FXML = "/fxml/login.fxml";
    private static final String STYLESHEET = "/css/eduaccess.css";

    private Stage primaryStage;
    private Scene scene;
    private AppContext ctx;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        try {
            boolean demoSeed = Boolean.parseBoolean(System.getProperty("eduaccess.demoSeed", "true"));
            DatabaseInitializer.initialize(DB_FILE, demoSeed);
            ctx = new AppContext(new DatabaseManager(DB_FILE));
            scene = new Scene(loadLogin(), 1280, 800);
        } catch (IOException | SQLException e) {
            e.printStackTrace();
            showStartupError("The database or the sign-in screen could not be loaded: " + e.getMessage());
            return;
        }
        scene.getStylesheets().add(EduAccessApp.class.getResource(STYLESHEET).toExternalForm());

        stage.setTitle("EduAccess - Sign in");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();
    }

    /** Loads login.fxml. The controller is created here so it receives the shared AppContext. */
    private Parent loadLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(EduAccessApp.class.getResource(LOGIN_FXML));
        loader.setControllerFactory(type -> new LoginController(ctx, this::showShell, this::showRegistration));
        return loader.load();
    }

    private void showLogin() {
        ctx.session.signOut();
        try {
            scene.setRoot(loadLogin());
            primaryStage.setTitle("EduAccess - Sign in");
        } catch (IOException e) {
            e.printStackTrace();
            showStartupError("The sign-in screen could not be loaded: " + e.getMessage());
        }
    }

    private void showRegistration() {
        scene.setRoot(new RegistrationView(ctx, this::showLogin).build());
    }

    private void showShell() {
        scene.setRoot(new AppShell(ctx, this::showLogin).build());
        primaryStage.setTitle("EduAccess - " + ctx.session.require().fullName());
    }

    private void showStartupError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText("EduAccess could not start");
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
