package com.eduaccess.ui;

import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Top bar, left navigation and the content area. The navigation depends on the role: student or library admin. */
public final class AppShell {

    private static final Insets PAGE_PADDING = new Insets(28, 36, 40, 36);

    private final AppContext ctx;
    private final Runnable onSignOut;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<Screen, Button> navButtons = new EnumMap<>(Screen.class);

    public AppShell(AppContext ctx, Runnable onSignOut) {
        this.ctx = ctx;
        this.onSignOut = onSignOut;
    }

    public Parent build() {
        User user = ctx.session.require();
        root.getStyleClass().add("page");
        root.setTop(topBar(user));
        root.setLeft(sidebar(user));
        root.setCenter(content);
        go(Screen.DASHBOARD);
        return root;
    }

    /** Opens a top-level screen and highlights its button. */
    void go(Screen screen) {
        highlight(screen);
        Node view;
        try {
            view = viewFor(screen);
        } catch (RuntimeException e) {
            e.printStackTrace();
            view = Ui.banner("This screen could not be loaded: " + e.getMessage(), "danger");
        }
        show(view);
    }

    /** Shows a sub-view, such as an item detail, inside the current screen. */
    void show(Node view) {
        VBox page = new VBox(view);
        page.setPadding(PAGE_PADDING);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        content.getChildren().setAll(scroll);
    }

    private Node viewFor(Screen screen) {
        boolean admin = ctx.session.require().role() == Role.ADMIN;
        return switch (screen) {
            case DASHBOARD -> admin ? new AdminDashboardView(ctx, this).build() : new DashboardView(ctx, this).build();
            case CATALOG -> new CatalogView(ctx, this).build();
            case DONATE -> new DonateView(ctx, this).build();
            case MY_LOANS -> new MyLoansView(ctx, this).build();
            case LIBRARY -> new LibraryView(ctx, this).build();
            case AI -> new AiPlaceholderView().build();
            case INVENTORY -> new InventoryView(ctx, this).build();
            case IMPACT -> new ImpactView(ctx).build();
        };
    }

    private Node topBar(User user) {
        Label brand = new Label("EduAccess");
        brand.getStyleClass().add("brand-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label who = Ui.muted(user.fullName() + "  ·  " + roleText(user));
        who.setWrapText(false);
        Button signOut = new Button("Sign out");
        signOut.getStyleClass().add("button-secondary");
        signOut.setOnAction(event -> {
            ctx.session.signOut();
            onSignOut.run();
        });
        HBox bar = new HBox(16, brand, spacer, who, signOut);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node sidebar(User user) {
        VBox nav = new VBox(6);
        nav.getStyleClass().add("sidebar");
        nav.getChildren().add(sectionLabel("MAIN MENU"));
        List<Screen> main = user.role() == Role.ADMIN
                ? List.of(Screen.DASHBOARD, Screen.CATALOG, Screen.LIBRARY, Screen.AI)
                : List.of(Screen.DASHBOARD, Screen.CATALOG, Screen.DONATE, Screen.MY_LOANS, Screen.LIBRARY, Screen.AI);
        for (Screen screen : main) {
            nav.getChildren().add(navButton(screen));
        }

        if (user.role() == Role.ADMIN) {
            nav.getChildren().add(sectionLabel("ADMINISTRATION"));
            nav.getChildren().add(navButton(Screen.INVENTORY));
            nav.getChildren().add(navButton(Screen.IMPACT));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label sdg = new Label("4   Supporting SDG 4: Quality Education");
        sdg.getStyleClass().add("sdg-note");
        sdg.setWrapText(true);
        sdg.setPadding(new Insets(10, 8, 0, 8));
        nav.getChildren().addAll(spacer, sdg);
        return nav;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-section");
        return label;
    }

    private Button navButton(Screen screen) {
        Button button = new Button(screen.label());
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> go(screen));
        navButtons.put(screen, button);
        return button;
    }

    private void highlight(Screen screen) {
        navButtons.forEach((key, button) -> {
            button.getStyleClass().remove("nav-active");
            if (key == screen) {
                button.getStyleClass().add("nav-active");
            }
        });
    }

    private static String roleText(User user) {
        return user.role() == Role.ADMIN ? "Library Admin" : "Student";
    }
}
