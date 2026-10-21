package com.eduaccess.ui;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.Intention;
import com.eduaccess.service.AuthService;
import com.eduaccess.service.ServiceException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Screen 2: the three-step account wizard. Account, academic profile, then how the student will use EduAccess. */
public final class RegistrationView {

    private static final Map<EducationLevel, List<String>> PROGRAMS = Map.of(
            EducationLevel.SHS, List.of("STEM", "ABM", "HUMSS", "TVL-ICT"),
            EducationLevel.COLLEGE, List.of("BS Computer Science", "BS Education", "BS Business Administration",
                    "BS Accountancy", "BS Engineering"));

    private static final String[] STEP_NAMES = {"1  Account", "2  Academic level", "3  How you will use EduAccess"};

    private final AppContext ctx;
    private final Runnable onDone;

    private final TextField schoolId = new TextField();
    private final TextField fullName = new TextField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final ToggleGroup levelGroup = new ToggleGroup();
    private final ComboBox<String> program = new ComboBox<>();
    private final ComboBox<Integer> year = new ComboBox<>();
    private final ComboBox<String> campus = new ComboBox<>();
    private final ToggleGroup intentionGroup = new ToggleGroup();
    private final CheckBox under18 = new CheckBox("I am under 18 years old");
    private final TextField guardianName = new TextField();
    private final ComboBox<String> relationship = new ComboBox<>();
    private final TextField guardianContact = new TextField();
    private final CheckBox guardianAgrees = new CheckBox();
    /** Shows or hides the guardian fields. Replaced each time the academic step is rebuilt. */
    private Runnable guardianUpdate = () -> { };

    private final Label error = new Label();
    private final StackPane stepArea = new StackPane();
    private final Label[] stepLabels = new Label[STEP_NAMES.length];
    private final Button back = new Button("Back");
    private final Button next = new Button("Continue");
    private int step;

    public RegistrationView(AppContext ctx, Runnable onDone) {
        this.ctx = ctx;
        this.onDone = onDone;
    }

    public Parent build() {
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        Hyperlink signIn = new Hyperlink("Back to sign in");
        signIn.setOnAction(event -> onDone.run());
        Hyperlink terms = new Hyperlink("Terms of Use");
        terms.setOnAction(event -> TermsDialog.show());
        Label brand = new Label("EduAccess");
        brand.getStyleClass().add("brand-name");
        HBox top = Ui.row(12, brand, terms, signInSpacer(), signIn);

        Label title = Ui.heading("Create your account");
        Label subtitle = Ui.muted("Tell us your school level so we can show materials that fit you.");
        HBox stepper = new HBox(18);
        for (int i = 0; i < STEP_NAMES.length; i++) {
            stepLabels[i] = Ui.muted(STEP_NAMES[i]);
            stepper.getChildren().add(stepLabels[i]);
        }

        back.getStyleClass().add("button-secondary");
        back.setOnAction(event -> go(-1));
        next.getStyleClass().add("button-primary");
        next.setOnAction(event -> go(1));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox buttons = Ui.row(10, back, gap, next);

        BorderPane page = new BorderPane();
        page.getStyleClass().add("page");
        page.setPadding(new Insets(24, 40, 24, 40));
        page.setTop(top);
        VBox center = new VBox(14, title, subtitle, stepper, stepArea, error, buttons);
        center.setPadding(new Insets(24, 0, 0, 0));
        page.setCenter(center);

        configureAcademicControls();
        show(0);
        return page;
    }

    private static Region signInSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void go(int delta) {
        error.setText("");
        if (delta > 0) {
            String problem = problemWithStep(step);
            if (problem != null) {
                error.setText(problem);
                return;
            }
            if (step == STEP_NAMES.length - 1) {
                submit();
                return;
            }
        }
        if (step == 0 && delta < 0) {
            onDone.run();
            return;
        }
        show(step + delta);
    }

    private void show(int target) {
        step = target;
        for (int i = 0; i < stepLabels.length; i++) {
            stepLabels[i].getStyleClass().remove("step-current");
            if (i == step) {
                stepLabels[i].getStyleClass().add("step-current");
            }
        }
        stepArea.getChildren().setAll(switch (step) {
            case 0 -> accountStep();
            case 1 -> academicStep();
            default -> usageStep();
        });
        next.setText(step == STEP_NAMES.length - 1 ? "Create account" : "Continue");
        back.setText(step == 0 ? "Cancel" : "Back");
    }

    private String problemWithStep(int current) {
        return switch (current) {
            case 0 -> accountProblem();
            case 1 -> academicProblem();
            default -> intentionGroup.getSelectedToggle() == null ? "Choose how you will use EduAccess." : null;
        };
    }

    private String accountProblem() {
        if (schoolId.getText().isBlank()) {
            return "Enter your school ID or student number.";
        }
        if (fullName.getText().isBlank()) {
            return "Enter your full name.";
        }
        if (password.getText().length() < 8) {
            return "Use a password of at least 8 characters.";
        }
        if (!password.getText().equals(confirm.getText())) {
            return "The two passwords do not match.";
        }
        return null;
    }

    private String academicProblem() {
        if (levelGroup.getSelectedToggle() == null) {
            return "Choose your education level.";
        }
        if (year.getValue() == null) {
            return "Choose your year level.";
        }
        if (campus.getValue() == null) {
            return "Choose your campus.";
        }
        if (needsGuardian()) {
            if (guardianName.getText().isBlank()) {
                return "Enter your parent's or guardian's full name.";
            }
            if (relationship.getValue() == null) {
                return "Choose how your guardian is related to you.";
            }
            if (guardianContact.getText().isBlank()) {
                return "Enter your guardian's mobile or phone number.";
            }
            if (!guardianAgrees.isSelected()) {
                return "Your parent or guardian must agree before you can register.";
            }
        }
        return null;
    }

    /** Elementary and Junior High always need a guardian. Senior High and College only when under 18. */
    private boolean needsGuardian() {
        if (levelGroup.getSelectedToggle() == null) {
            return false;
        }
        EducationLevel level = (EducationLevel) levelGroup.getSelectedToggle().getUserData();
        return AuthService.alwaysMinor(level) || under18.isSelected();
    }

    private VBox accountStep() {
        GridPane grid = formGrid();
        grid.add(fieldLabel("School ID / Student number"), 0, 0);
        grid.add(schoolId, 0, 1);
        grid.add(fieldLabel("Full name"), 0, 2);
        grid.add(fullName, 0, 3);
        grid.add(fieldLabel("Password (at least 8 characters)"), 0, 4);
        grid.add(password, 0, 5);
        grid.add(fieldLabel("Confirm password"), 0, 6);
        grid.add(confirm, 0, 7);
        return Ui.column(10, Ui.card(grid));
    }

    private VBox academicStep() {
        HBox levels = new HBox(12);
        for (EducationLevel level : EducationLevel.values()) {
            ToggleButton card = new ToggleButton(levelTitle(level) + "\n" + levelRange(level));
            card.getStyleClass().add("toggle-card");
            card.setToggleGroup(levelGroup);
            card.setUserData(level);
            card.setPrefWidth(200);
            HBox.setHgrow(card, Priority.ALWAYS);
            levels.getChildren().add(card);
        }
        GridPane details = formGrid();
        details.add(fieldLabel("Program / strand"), 0, 0);
        details.add(program, 1, 0);
        details.add(fieldLabel("Year level"), 0, 1);
        details.add(year, 1, 1);
        details.add(fieldLabel("School / Campus"), 0, 2);
        details.add(campus, 1, 2);
        program.setPrefWidth(320);
        year.setPrefWidth(320);
        campus.setPrefWidth(320);
        return Ui.column(12, Ui.section("1. Select your education level"), levels,
                Ui.section("2. Program details"), Ui.card(details), ageSection());
    }

    /**
     * Section 3 of the academic step: learners under 18 register with a parent's or guardian's consent, as
     * RA 10173 (the Data Privacy Act) requires. Only the guardian's name, relationship and number are kept.
     */
    private VBox ageSection() {
        Label always = Ui.muted("Learners in Elementary and Junior High need a parent's or guardian's consent.");
        GridPane grid = formGrid();
        grid.setHgap(14);
        grid.setVgap(10);
        guardianName.setPromptText("e.g. Maria Santos");
        guardianContact.setPromptText("e.g. 0917 123 4567");
        guardianName.setPrefWidth(280);
        relationship.setPrefWidth(200);
        guardianContact.setPrefWidth(220);
        grid.add(Ui.labelled("Parent or guardian's full name", guardianName), 0, 0);
        grid.add(Ui.labelled("Relationship", relationship), 1, 0);
        grid.add(Ui.labelled("Mobile or phone number", guardianContact), 2, 0);
        guardianAgrees.setText("My parent or guardian has read how EduAccess uses my information and agrees to "
                + "this account. Only my education level and program are used to choose materials.");
        guardianAgrees.setWrapText(true);
        guardianAgrees.setMaxWidth(760);
        VBox guardian = new VBox(12, grid, guardianAgrees);
        guardian.getStyleClass().add("card-flat");

        guardianUpdate = () -> {
            EducationLevel level = levelGroup.getSelectedToggle() == null ? null
                    : (EducationLevel) levelGroup.getSelectedToggle().getUserData();
            boolean young = level != null && AuthService.alwaysMinor(level);
            always.setVisible(young);
            always.setManaged(young);
            under18.setVisible(level != null && !young);
            under18.setManaged(level != null && !young);
            guardian.setVisible(needsGuardian());
            guardian.setManaged(needsGuardian());
        };
        guardianUpdate.run();
        return Ui.column(10, Ui.section("3. Age and guardian consent"), always, under18, guardian);
    }

    private VBox usageStep() {
        ToggleButton borrower = intentionButton("Borrow items", "Borrow calculators, laptops and books.",
                Intention.BORROWER);
        ToggleButton donor = intentionButton("Donate", "Give books, calculators and devices to the library.",
                Intention.DONOR);
        ToggleButton both = intentionButton("Both", "Borrow and donate.", Intention.BOTH);
        HBox choices = new HBox(12, borrower, donor, both);
        Label privacy = Ui.banner("Your data stays private. Only your education level and program are used to "
                + "choose the materials shown to you.", "info");
        return Ui.column(12, Ui.section("3. How will you use EduAccess?"), choices, privacy);
    }

    private ToggleButton intentionButton(String title, String detail, Intention intention) {
        ToggleButton button = new ToggleButton(title + "\n" + detail);
        button.getStyleClass().add("toggle-card");
        button.setToggleGroup(intentionGroup);
        button.setUserData(intention);
        button.setPrefWidth(260);
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }

    private void configureAcademicControls() {
        levelGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null) {
                return;
            }
            EducationLevel level = (EducationLevel) now.getUserData();
            program.getItems().setAll(PROGRAMS.getOrDefault(level, List.of()));
            program.setDisable(!PROGRAMS.containsKey(level));
            program.setValue(null);
            int[] range = yearRange(level);
            year.getItems().setAll(IntStream.rangeClosed(range[0], range[1]).boxed().toList());
            year.setValue(null);
        });
        program.setDisable(true);
        campus.getItems().setAll("Main Campus", "North Campus");
        relationship.getItems().setAll("Mother", "Father", "Legal guardian", "Grandparent", "Other relative");
        relationship.setPromptText("Choose");
        levelGroup.selectedToggleProperty().addListener((obs, was, now) -> guardianUpdate.run());
        under18.selectedProperty().addListener((obs, was, now) -> guardianUpdate.run());
        year.setPromptText("Choose level first");
    }

    private void submit() {
        EducationLevel level = (EducationLevel) levelGroup.getSelectedToggle().getUserData();
        Intention intention = (Intention) intentionGroup.getSelectedToggle().getUserData();
        AuthService.RegistrationForm form = new AuthService.RegistrationForm(
                schoolId.getText(),
                fullName.getText(),
                password.getText().toCharArray(),
                level,
                program.isDisabled() ? null : program.getValue(),
                year.getValue(),
                campus.getValue(),
                intention);
        AuthService.GuardianConsent guardian = needsGuardian()
                ? new AuthService.GuardianConsent(guardianName.getText(), relationship.getValue(),
                        guardianContact.getText(), guardianAgrees.isSelected())
                : null;
        try {
            ctx.auth.register(form, guardian);
            Ui.info("Your account is ready. Sign in with your school ID.");
            onDone.run();
        } catch (ServiceException e) {
            error.setText(e.getMessage());
            show(e.getMessage().toLowerCase(Locale.ROOT).contains("guardian") ? 1 : 0);
        }
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        return grid;
    }

    private static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private static String levelTitle(EducationLevel level) {
        return switch (level) {
            case ELEMENTARY -> "Elementary";
            case JHS -> "Junior High School";
            case SHS -> "Senior High School";
            case COLLEGE -> "College / Undergraduate";
        };
    }

    private static String levelRange(EducationLevel level) {
        return switch (level) {
            case ELEMENTARY -> "Grades 1 to 6";
            case JHS -> "Grades 7 to 10";
            case SHS -> "STEM, ABM, HUMSS, TVL";
            case COLLEGE -> "By program and year";
        };
    }

    private static int[] yearRange(EducationLevel level) {
        return switch (level) {
            case ELEMENTARY -> new int[] {1, 6};
            case JHS -> new int[] {7, 10};
            case SHS -> new int[] {11, 12};
            case COLLEGE -> new int[] {1, 5};
        };
    }
}
