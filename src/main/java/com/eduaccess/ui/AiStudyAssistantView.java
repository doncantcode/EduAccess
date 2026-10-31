package com.eduaccess.ui;

import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import com.eduaccess.service.AiRecommendationService;
import com.eduaccess.service.AiRecommendationService.RecommendationResult;
import com.eduaccess.service.ServiceException;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Screen 9: AI Study Assistant.
 * Students ask for guidance on learning problems, exam topics, or subjects, and receive personalized
 * study advice along with interactive cards of available books from the Physical Library shelves.
 */
final class AiStudyAssistantView {

    private final AppContext ctx;
    private final AppShell shell;

    private final TextArea promptInput = new TextArea();
    private final Button askButton = new Button("Ask AI Assistant", Icons.SPARKLE.node(16));
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final VBox resultsBox = new VBox(18);
    private final Label engineBadge = new Label();

    AiStudyAssistantView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        Button settingsBtn = new Button("API Settings", Icons.PIN.node(14));
        settingsBtn.getStyleClass().addAll("button-secondary", "button-small");
        settingsBtn.setOnAction(event -> openSettingsDialog());

        HBox header = Ui.pageHeader(
                "AI Study Assistant",
                "Describe your study topic or learning problem to get personalized guidance and discover available library books.",
                engineBadge,
                settingsBtn
        );
        updateEngineBadge();

        Node contextBanner = studentContextCard();
        Node inputCard = buildInputCard();

        // Initial welcome card in results
        resultsBox.getChildren().setAll(initialWelcomeCard());

        VBox page = new VBox(20, header, contextBanner, inputCard, resultsBox);
        page.setPadding(new Insets(0, 0, 40, 0));
        return page;
    }

    private void updateEngineBadge() {
        String key = ctx.ai.getApiKey();
        if (key != null && !key.isBlank()) {
            engineBadge.setText("✨ Gemini AI Connected");
            engineBadge.getStyleClass().setAll("badge", "badge-active");
        } else {
            engineBadge.setText("📚 Offline Matcher Mode");
            engineBadge.getStyleClass().setAll("badge", "badge-returned");
        }
    }

    private Node studentContextCard() {
        User user = ctx.session.require();
        String level = user.educationLevel() == null ? "All Levels" : user.educationLevel().dbValue();
        String program = user.program() == null || user.program().isBlank() ? "" : "  ·  " + user.program();
        String grade = user.yearLevel() == null ? "" : " (Year/Grade " + user.yearLevel() + ")";

        Label info = new Label("Logged in as " + user.fullName() + "  ·  " + level + grade + program);
        info.getStyleClass().add("bold");

        Label note = Ui.muted("Recommendations automatically prioritize material suited for your academic curriculum.");
        VBox card = new VBox(4, info, note);
        card.getStyleClass().addAll("card", "teal-card-soft");
        return card;
    }

    private Node buildInputCard() {
        Label sectionTitle = Ui.section("What do you need help studying today?");

        Label chipsLabel = Ui.fieldLabel("Popular topics & quick suggestions (click to try):");
        FlowPane chips = new FlowPane(8, 8);
        List<String> suggestions = List.of(
                "Derivatives & Calculus problem solving",
                "Java programming & Object-Oriented Design",
                "El Filibusterismo & Philippine literature",
                "Senior High General Chemistry reviewer",
                "Basic Accounting & Managerial Finance",
                "Grade 8 Mathematics patterns and algebra",
                "Araling Panlipunan: Philippine history & economy"
        );
        for (String promptText : suggestions) {
            Button chip = new Button(promptText);
            chip.getStyleClass().add("chip");
            chip.setOnAction(e -> {
                promptInput.setText(promptText);
                submitQuery();
            });
            chips.getChildren().add(chip);
        }

        promptInput.setPromptText("Type your question, topic, or learning obstacle (e.g. 'I am struggling with derivatives and need practice problems' or 'Looking for beginner books on programming')...");
        promptInput.setPrefRowCount(3);
        promptInput.setWrapText(true);

        spinner.setVisible(false);
        spinner.setManaged(false);
        spinner.setMaxSize(24, 24);

        askButton.getStyleClass().addAll("button-teal");
        askButton.setOnAction(e -> submitQuery());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().addAll("button-secondary");
        clearBtn.setOnAction(e -> {
            promptInput.clear();
            resultsBox.getChildren().setAll(initialWelcomeCard());
        });

        HBox actions = Ui.row(12, askButton, clearBtn, spinner);

        VBox box = Ui.card(sectionTitle, chipsLabel, chips, promptInput, actions);
        return box;
    }

    private Node initialWelcomeCard() {
        Label title = Ui.section("How the AI Study Assistant Works");
        Label desc = new Label(
                "1. Type any topic, concept, or specific homework issue you are facing.\n"
                + "2. The AI reads our current library stock to find physical books on the shelves that match your grade level.\n"
                + "3. You receive practical study advice and clickable book cards with real-time shelf availability."
        );
        desc.setWrapText(true);
        desc.getStyleClass().add("small");
        return Ui.card(title, desc);
    }

    private void submitQuery() {
        String text = promptInput.getText();
        if (text == null || text.isBlank()) {
            Ui.info("Please enter a study topic, assignment, or question.");
            return;
        }

        setLoading(true);

        User user = ctx.session.require();
        Thread.startVirtualThread(() -> {
            try {
                RecommendationResult result = ctx.ai.recommendBooks(user, text);
                Platform.runLater(() -> {
                    setLoading(false);
                    displayResults(result);
                });
            } catch (ServiceException se) {
                Platform.runLater(() -> {
                    setLoading(false);
                    Ui.info(se.getMessage());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setLoading(false);
                    Ui.error("Could not complete recommendation: " + ex.getMessage());
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        askButton.setDisable(loading);
        promptInput.setDisable(loading);
        spinner.setVisible(loading);
        spinner.setManaged(loading);

        if (loading) {
            Label searching = new Label("Consulting library catalog and generating recommendations...");
            searching.getStyleClass().addAll("muted", "italic");
            VBox box = Ui.card(Ui.row(12, new ProgressIndicator(), searching));
            box.setAlignment(Pos.CENTER);
            resultsBox.getChildren().setAll(box);
        }
    }

    private void displayResults(RecommendationResult result) {
        resultsBox.getChildren().clear();

        // 1. AI Advice & Study Guidance Card
        Label adviceHeader = Ui.section("Study Guidance");
        Label engineTag = new Label(result.usedCloudAi() ? "✨ " + result.engineName() : "📚 " + result.engineName());
        engineTag.getStyleClass().addAll("badge", result.usedCloudAi() ? "badge-active" : "badge-returned");

        HBox adviceTitleRow = Ui.row(12, adviceHeader, Ui.grow(), engineTag);

        Label adviceText = new Label(result.advice());
        adviceText.setWrapText(true);
        adviceText.setStyle("-fx-line-spacing: 4px; -fx-font-size: 14px;");

        VBox adviceCard = Ui.card(adviceTitleRow, adviceText);
        resultsBox.getChildren().add(adviceCard);

        // 2. Recommended Books Grid or "No Books Found" Notice
        List<PhysicalItem> books = result.recommendedBooks();
        if (books.isEmpty()) {
            Label noBooksBanner = Ui.banner(
                    "The library doesn't have a book on that particular topic.",
                    "warning"
            );
            noBooksBanner.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

            Label noticeHeader = Ui.section("No Physical Books Available for This Topic");
            Label noticeBody = new Label(
                    "We searched the entire physical library catalog for \"" + result.query() + "\", "
                    + "but the library does not currently have a physical book on this particular topic in our collection."
            );
            noticeBody.setWrapText(true);
            noticeBody.setStyle("-fx-font-size: 13px;");

            Label optionsLabel = Ui.muted("You can still find learning resources through other parts of EduAccess:");

            Button digitalBtn = new Button("Explore Digital Library", Icons.TABLET.node(16));
            digitalBtn.getStyleClass().addAll("button-primary", "button-small");
            digitalBtn.setOnAction(e -> shell.go(Screen.LIBRARY));

            Button marketBtn = new Button("Check Student Marketplace", Icons.USERS.node(16));
            marketBtn.getStyleClass().addAll("button-secondary", "button-small");
            marketBtn.setOnAction(e -> shell.go(Screen.MARKETPLACE));

            Button allBooksBtn = new Button("Browse All Library Books", Icons.BOOK.node(16));
            allBooksBtn.getStyleClass().addAll("button-secondary", "button-small");
            allBooksBtn.setOnAction(e -> shell.go(Screen.PHYSICAL_LIBRARY));

            HBox actions = Ui.row(10, digitalBtn, marketBtn, allBooksBtn);

            VBox emptyCard = new VBox(12, noticeHeader, noticeBody, optionsLabel, actions);
            emptyCard.getStyleClass().addAll("card", "teal-card-soft");

            VBox emptySection = new VBox(12, noBooksBanner, emptyCard);
            resultsBox.getChildren().add(emptySection);
        } else {
            Label booksHeader = Ui.section("Recommended Available Books (" + books.size() + ")");
            Label booksSub = Ui.muted("These books are available right now on the library shelves. Click 'View & Borrow' to check details or borrow.");

            FlowPane grid = new FlowPane(16, 16);
            for (PhysicalItem book : books) {
                grid.getChildren().add(bookCard(book));
            }

            VBox booksSection = new VBox(12, new VBox(4, booksHeader, booksSub), grid);
            resultsBox.getChildren().add(booksSection);
        }
    }

    private VBox bookCard(PhysicalItem item) {
        Label pill = new Label(item.category().dbValue().toUpperCase());
        pill.getStyleClass().add("category-pill");

        Label title = new Label(item.title());
        title.getStyleClass().add("item-title");
        title.setWrapText(true);

        Label brand = Ui.muted(item.brandModel() == null ? item.category().dbValue() : item.brandModel());
        Label detail = Ui.muted(Ui.levelText(item.levels()) + "  ·  " + item.condition().dbValue());
        detail.getStyleClass().add("small");

        Label location = Ui.muted("📍 " + item.pickupPoint());
        location.getStyleClass().add("small");

        Button view = new Button("View & Borrow");
        view.getStyleClass().addAll("button-primary", "button-small");
        view.setOnAction(e -> shell.show(new ItemDetailView(ctx, shell, item.id()).build()));

        HBox footer = Ui.row(8, Ui.itemBadge(item.status()), Ui.grow(), view);

        VBox text = new VBox(4, pill, title, brand, detail, location);
        VBox.setVgrow(text, Priority.ALWAYS);

        VBox card = new VBox(12, Ui.art(item.category(), 212, 110), text, footer);
        card.getStyleClass().addAll("card", "item-card", "card-hover");
        card.setPrefWidth(240);
        card.setMaxWidth(240);
        return card;
    }

    private void openSettingsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("AI Study Assistant Settings");
        Ui.style(dialog);

        Label title = Ui.heading("Gemini AI API Configuration");
        Label explanation = Ui.muted("Enter a Google Gemini API key to use cloud AI. If left empty, EduAccess uses its built-in offline matching engine.");

        TextField keyField = new TextField(ctx.ai.getApiKey());
        keyField.setPromptText("Enter Gemini API Key (e.g. AQ... or AIza...)");
        keyField.setPrefWidth(380);

        Label currentModel = Ui.muted("Default model: " + ctx.ai.getModelName());

        VBox content = new VBox(12, title, explanation, Ui.labelled("Gemini API Key", keyField), currentModel);
        content.setPadding(new Insets(16));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ctx.ai.setApiKey(keyField.getText().strip());
                updateEngineBadge();
            }
        });
    }
}
