package com.eduaccess.ui;

import com.eduaccess.model.Catalog;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalOffer;
import com.eduaccess.model.RentalRequest;
import com.eduaccess.model.Role;
import com.eduaccess.model.User;
import com.eduaccess.service.RentalService;
import com.eduaccess.service.ServiceException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * The main window after sign-in, built on a {@link BorderPane}:
 * <ul>
 *   <li>left: the navy sidebar with the main tabs (and the admin tabs for library staff)</li>
 *   <li>top: global search, the English/Filipino toggle, notifications and the signed-in profile</li>
 *   <li>center: the current screen, inside a scroll pane</li>
 *   <li>bottom: a thin status bar</li>
 * </ul>
 */
public final class AppShell {

    private static final Insets PAGE_PADDING = new Insets(28, 32, 40, 32);
    private static final List<Screen> STUDENT_MENU = List.of(Screen.DASHBOARD, Screen.PHYSICAL_LIBRARY, Screen.CATALOG,
            Screen.RENTAL_REQUESTS, Screen.DONATE, Screen.MY_LOANS, Screen.LIBRARY, Screen.AI);
    private static final List<Screen> STAFF_MENU = List.of(Screen.DASHBOARD, Screen.PHYSICAL_LIBRARY, Screen.CATALOG,
            Screen.RENTAL_REQUESTS, Screen.LIBRARY, Screen.AI);
    private static final List<Screen> STAFF_ADMIN_MENU = List.of(Screen.INVENTORY, Screen.IMPACT);

    private final AppContext ctx;
    private final Runnable onSignOut;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final BorderPane main = new BorderPane();
    private final Map<Screen, Button> navButtons = new EnumMap<>(Screen.class);
    private final Label bellCount = new Label();
    private final TextField search = new TextField();
    private Screen current = Screen.DASHBOARD;

    public AppShell(AppContext ctx, Runnable onSignOut) {
        this.ctx = ctx;
        this.onSignOut = onSignOut;
    }

    public Parent build() {
        root.getStyleClass().add("page");
        // The sidebar runs the full height. The header, the screen and the status bar share the space beside it.
        main.setCenter(content);
        main.setBottom(statusBar());
        root.setCenter(main);
        rebuildChrome();
        go(Screen.DASHBOARD);
        // Ctrl+K jumps to the global search, as the status bar says.
        root.sceneProperty().addListener((obs, was, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                        search::requestFocus);
            }
        });
        return root;
    }

    /** Opens a top-level screen and highlights its button. */
    void go(Screen screen) {
        go(screen, null);
    }

    /** Opens a screen with a green confirmation banner on top, for example after a form is submitted. */
    void go(Screen screen, String successMessage) {
        current = screen;
        highlight(screen);
        Node view;
        try {
            view = viewFor(screen);
        } catch (RuntimeException e) {
            e.printStackTrace();
            view = Ui.banner("This screen could not be loaded: " + e.getMessage(), "danger");
        }
        if (successMessage != null) {
            view = new VBox(18, Ui.banner(successMessage, "success"), view);
        }
        show(view);
        refreshCounts();
    }

    /** Shows a sub-view, such as an item detail, inside the current screen. */
    void show(Node view) {
        VBox page = new VBox(view);
        page.setPadding(PAGE_PADDING);
        page.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        content.getChildren().setAll(scroll);
    }

    /** Runs the global search and shows the results. */
    void search(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        highlight(null);
        show(new SearchResultsView(ctx, this, query.strip()).build());
    }

    private Node viewFor(Screen screen) {
        boolean admin = isAdmin();
        return switch (screen) {
            case DASHBOARD -> admin ? new AdminDashboardView(ctx, this).build() : new DashboardView(ctx, this).build();
            case PHYSICAL_LIBRARY -> new LibraryHubView(ctx, this, LibraryHubView.Tab.BOOKS).build();
            case RENTAL_REQUESTS -> new LibraryHubView(ctx, this, LibraryHubView.Tab.RENTALS).build();
            case CATALOG -> new CatalogView(ctx, this, Catalog.HUB).build();
            case DONATE -> new DonateView(ctx, this).build();
            case MY_LOANS -> new MyLoansView(ctx, this).build();
            case LIBRARY -> new LibraryView(ctx, this).build();
            case AI -> new AiPlaceholderView().build();
            case INVENTORY -> new InventoryView(ctx, this).build();
            case IMPACT -> new ImpactView(ctx).build();
        };
    }

    // ----- Sidebar ---------------------------------------------------------------------------------------------

    /** Builds the sidebar and the header. Called again when the language changes. */
    private void rebuildChrome() {
        User user = ctx.session.require();
        navButtons.clear();
        root.setLeft(sidebar(user));
        main.setTop(header(user));
        highlight(current);
        refreshCounts();
    }

    private Node sidebar(User user) {
        VBox nav = new VBox(3);
        nav.getStyleClass().add("sidebar");
        nav.getChildren().add(brand());
        nav.getChildren().add(sectionLabel(Lang.t("MAIN MENU")));
        for (Screen screen : user.role() == Role.ADMIN ? STAFF_MENU : STUDENT_MENU) {
            nav.getChildren().add(navButton(screen));
        }
        if (user.role() == Role.ADMIN) {
            nav.getChildren().add(sectionLabel(Lang.t("ADMINISTRATION")));
            for (Screen screen : STAFF_ADMIN_MENU) {
                nav.getChildren().add(navButton(screen));
            }
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button signOut = new Button(Lang.t("Sign out"), Icons.LOGOUT.node(18));
        signOut.getStyleClass().add("nav-button");
        signOut.setMaxWidth(Double.MAX_VALUE);
        signOut.setOnAction(event -> {
            ctx.session.signOut();
            onSignOut.run();
        });

        Label number = new Label("4");
        number.getStyleClass().add("sdg-number");
        Label note = new Label(Lang.t("Supporting SDG 4: Quality Education"));
        note.getStyleClass().add("sdg-note");
        note.setWrapText(true);
        HBox sdg = new HBox(number, note);
        sdg.getStyleClass().add("sdg-card");

        nav.getChildren().addAll(spacer, signOut, sdg);
        VBox.setMargin(sdg, new Insets(8, 0, 0, 0));
        return nav;
    }

    private Node brand() {
        StackPane mark = new StackPane(Icons.BOOK.node(20));
        mark.getStyleClass().add("brand-mark");
        Label edu = new Label("Edu");
        edu.getStyleClass().add("sidebar-brand-name");
        Label access = new Label("Access");
        access.getStyleClass().add("sidebar-brand-accent");
        HBox brand = new HBox(mark, new HBox(edu, access));
        brand.getStyleClass().add("sidebar-brand");
        return brand;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-section");
        return label;
    }

    private Button navButton(Screen screen) {
        Button button = new Button(Lang.t(screen.label()), screen.icon().node(18));
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> go(screen));
        button.setMnemonicParsing(false);
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

    /** Red count pills on the sidebar: overdue loans for students, work waiting for staff. */
    private void refreshCounts() {
        try {
            User user = ctx.session.require();
            LocalDate today = LocalDate.now();
            if (user.role() == Role.ADMIN) {
                setNavCount(Screen.RENTAL_REQUESTS, ctx.rentals.offersAwaitingReview());
                setNavCount(Screen.INVENTORY, ctx.inventory.pendingApprovals().size());
            } else {
                long overdue = ctx.loans.myLoans(user, today).stream()
                        .filter(d -> d.loan().status() == LoanStatus.OVERDUE).count();
                setNavCount(Screen.MY_LOANS, (int) overdue);
            }
            int notices = notices(user, today).size();
            bellCount.setText(notices > 9 ? "9+" : String.valueOf(notices));
            bellCount.setVisible(notices > 0);
        } catch (ServiceException e) {
            bellCount.setVisible(false);
        }
    }

    private void setNavCount(Screen screen, int count) {
        Button button = navButtons.get(screen);
        if (button == null) {
            return;
        }
        if (count <= 0) {
            button.setGraphic(screen.icon().node(18));
            button.setContentDisplay(ContentDisplay.LEFT);
            return;
        }
        Label pill = new Label(String.valueOf(count));
        pill.getStyleClass().add("nav-count");
        Label text = new Label(Lang.t(screen.label()));
        text.getStyleClass().add("nav-label");
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox graphic = new HBox(10, screen.icon().node(18), text, pill);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMaxWidth(Double.MAX_VALUE);
        graphic.prefWidthProperty().bind(button.widthProperty().subtract(26));
        button.setGraphic(graphic);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    // ----- Header ----------------------------------------------------------------------------------------------

    private Node header(User user) {
        HBox bar = new HBox(searchBox(), Ui.grow(), languageToggle(), bell(user), profile(user));
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private Node searchBox() {
        search.setPromptText(Lang.t("Search items, e-books, modules..."));
        search.setOnAction(event -> search(search.getText()));
        HBox.setHgrow(search, Priority.ALWAYS);
        Label hint = new Label("Ctrl+K");
        hint.getStyleClass().add("search-hint");
        HBox box = new HBox(Icons.SEARCH.node(18), search, hint);
        box.getStyleClass().add("search-box");
        HBox.setHgrow(box, Priority.SOMETIMES);
        HBox.setMargin(hint, new Insets(0, 6, 0, 0));
        return box;
    }

    private Node languageToggle() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton en = new ToggleButton("EN");
        ToggleButton fil = new ToggleButton("FIL");
        en.setToggleGroup(group);
        fil.setToggleGroup(group);
        en.setTooltip(new Tooltip("English"));
        fil.setTooltip(new Tooltip("Filipino"));
        (Lang.current() == Lang.EN ? en : fil).setSelected(true);
        group.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                was.setSelected(true); // one option is always on
                return;
            }
            Lang chosen = now == en ? Lang.EN : Lang.FIL;
            if (chosen != Lang.current()) {
                Lang.set(chosen);
                rebuildChrome();
            }
        });
        HBox toggle = new HBox(en, fil);
        toggle.getStyleClass().add("lang-toggle");
        toggle.setAlignment(Pos.CENTER);
        return toggle;
    }

    private Node bell(User user) {
        Button button = new Button();
        button.setGraphic(Icons.BELL.node(20));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(Lang.t("Notifications")));
        button.setAccessibleText(Lang.t("Notifications"));
        bellCount.getStyleClass().setAll("notify-dot");
        bellCount.setMouseTransparent(true);
        StackPane stack = new StackPane(button, bellCount);
        StackPane.setAlignment(bellCount, Pos.TOP_RIGHT);
        StackPane.setMargin(bellCount, new Insets(-2, -4, 0, 0));
        button.setOnAction(event -> showNotifications(button, user));
        return stack;
    }

    private Node profile(User user) {
        Label avatar = new Label(initials(user.fullName()));
        avatar.getStyleClass().add("avatar");
        if (user.role() == Role.ADMIN) {
            avatar.getStyleClass().add("avatar-admin");
        }
        Label name = new Label(user.fullName());
        name.getStyleClass().add("profile-name");
        Label meta = new Label(profileMeta(user));
        meta.getStyleClass().add("profile-meta");
        HBox box = new HBox(avatar, new VBox(1, name, meta));
        box.getStyleClass().add("profile");
        return box;
    }

    /** "College · BS Computer Science · Year 1", "Grade 11 · STEM", or "Library Admin". */
    static String profileMeta(User user) {
        if (user.role() == Role.ADMIN) {
            return "Library Admin";
        }
        EducationLevel level = user.educationLevel();
        Integer year = user.yearLevel();
        if (level == null) {
            return "Student";
        }
        if (level == EducationLevel.COLLEGE) {
            String program = user.program() == null ? "" : " · " + abbreviate(user.program());
            return "College" + program + (year == null ? "" : " · Year " + year);
        }
        String grade = year == null ? level.dbValue() : "Grade " + year;
        return user.program() == null ? grade : grade + " · " + user.program();
    }

    /** "BS Computer Science" becomes "BSCS". Short names stay as they are. */
    private static String abbreviate(String program) {
        if (program.length() <= 12) {
            return program;
        }
        StringBuilder letters = new StringBuilder();
        for (String word : program.split("\\s+")) {
            if (word.equals("BS") || word.equals("AB")) {
                letters.append(word);
            } else if (!word.isEmpty() && Character.isUpperCase(word.charAt(0))) {
                letters.append(word.charAt(0));
            }
        }
        return letters.toString();
    }

    private static String initials(String fullName) {
        String[] parts = fullName.strip().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    // ----- Notifications ---------------------------------------------------------------------------------------

    private record Notice(String title, String body, Screen target) {
    }

    private List<Notice> notices(User user, LocalDate today) {
        List<Notice> list = new ArrayList<>();
        if (user.role() == Role.ADMIN) {
            int donations = ctx.inventory.pendingApprovals().size();
            if (donations > 0) {
                list.add(new Notice(donations + " donation(s) to review",
                        "Inspect them at the counter, then approve or reject.", Screen.INVENTORY));
            }
            int offers = ctx.rentals.offersAwaitingReview();
            if (offers > 0) {
                list.add(new Notice(offers + " rental offer(s) to review",
                        "Students offered items for your rental requests.", Screen.RENTAL_REQUESTS));
            }
            int overdue = ctx.loans.overdueLoans(today).size();
            if (overdue > 0) {
                list.add(new Notice(overdue + " overdue loan(s)", "Follow up with the borrowers.", Screen.INVENTORY));
            }
            return list;
        }
        ctx.loans.myLoans(user, today).forEach(d -> {
            if (d.loan().status() == LoanStatus.OVERDUE) {
                list.add(new Notice("\"" + d.itemTitle() + "\" is overdue",
                        Ui.daysText(today, d.loan().dueDate()) + ". Please return it to stop further fees.",
                        Screen.MY_LOANS));
            } else if (d.loan().status() == LoanStatus.ACTIVE && !d.loan().dueDate().isAfter(today.plusDays(2))) {
                list.add(new Notice("\"" + d.itemTitle() + "\" is due soon",
                        "Due " + Ui.date(d.loan().dueDate()) + ".", Screen.MY_LOANS));
            }
        });
        for (RentalOffer offer : ctx.rentals.myOffers(user)) {
            if (offer.status() == OfferStatus.APPROVED) {
                RentalRequest request = ctx.rentals.request(offer.requestId());
                list.add(new Notice("Your " + offer.itemName() + " was approved",
                        "Bring it to " + request.dropoffPoint() + " by " + Ui.date(request.neededFrom()) + ".",
                        Screen.RENTAL_REQUESTS));
            }
        }
        int open = RentalService.mayOffer(user) ? ctx.rentals.openRequests(today).size() : 0;
        if (open > 0) {
            list.add(new Notice("The library needs " + open + (open == 1 ? " item" : " items"),
                    "Offer something you own for a short rental.", Screen.RENTAL_REQUESTS));
        }
        return list;
    }

    private void showNotifications(Button anchor, User user) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("notify-menu");
        Label heading = new Label(Lang.t("Notifications"));
        heading.getStyleClass().add("section-title");
        CustomMenuItem head = new CustomMenuItem(heading, false);
        head.setDisable(true);
        heading.setStyle("-fx-opacity: 1;");
        menu.getItems().add(head);

        List<Notice> notices = notices(user, LocalDate.now());
        if (notices.isEmpty()) {
            CustomMenuItem none = new CustomMenuItem(Ui.muted("You're all caught up."), false);
            menu.getItems().add(none);
        }
        for (Notice notice : notices) {
            Label title = new Label(notice.title());
            title.getStyleClass().add("notify-title");
            Label body = new Label(notice.body());
            body.getStyleClass().add("notify-body");
            body.setWrapText(true);
            VBox box = new VBox(2, title, body);
            box.setPrefWidth(320);
            CustomMenuItem item = new CustomMenuItem(box, true);
            item.setOnAction(event -> go(notice.target()));
            menu.getItems().add(item);
        }
        menu.show(anchor, Side.BOTTOM, -280, 8);
    }

    // ----- Status bar ------------------------------------------------------------------------------------------

    private Node statusBar() {
        Circle dot = new Circle(4);
        dot.getStyleClass().add("status-dot");
        Label left = new Label("Offline-ready · Local database");
        Label right = new Label("Ctrl+K to search · Tab to move between controls");
        HBox bar = new HBox(dot, left, Ui.grow(), right);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private boolean isAdmin() {
        return ctx.session.require().role() == Role.ADMIN;
    }
}
