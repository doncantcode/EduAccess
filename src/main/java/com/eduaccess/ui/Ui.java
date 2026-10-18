package com.eduaccess.ui;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.OfferStatus;
import com.eduaccess.model.RentalCategory;
import com.eduaccess.model.RentalRequestStatus;
import com.eduaccess.service.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** Shared labels, badges, dialogs and formatting for the screens. */
final class Ui {

    /** The app stylesheet. The main scene uses it, and so must every dialog, which gets a scene of its own. */
    static final String STYLESHEET = "/css/styles.css";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private Ui() {
    }

    static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    /** "Oct 24 – Oct 27, 2026", or a single date when both ends are the same day. */
    static String dateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return date(from);
        }
        return from.format(SHORT_DATE) + " – " + date(to);
    }

    static String money(BigDecimal amount) {
        return "₱" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String daysText(LocalDate today, LocalDate due) {
        long days = ChronoUnit.DAYS.between(today, due);
        if (days == 0) {
            return "Due today";
        }
        if (days > 0) {
            return days + (days == 1 ? " day left" : " days left");
        }
        long late = -days;
        return late + (late == 1 ? " day late" : " days late");
    }

    static String levelText(List<EducationLevel> levels) {
        return levels.stream().map(EducationLevel::dbValue).collect(Collectors.joining(" · "));
    }

    static String sizeText(double megabytes) {
        if (megabytes < 1) {
            return Math.max(1, Math.round(megabytes * 1024)) + " KB";
        }
        return String.format(Locale.ENGLISH, "%.1f MB", megabytes);
    }

    /** A status badge: a coloured dot plus a text label, so colour is never the only signal. */
    static Label badge(String text, String variant) {
        Label label = new Label(text);
        Circle dot = new Circle(3.5);
        dot.getStyleClass().add("badge-dot");
        label.setGraphic(dot);
        label.getStyleClass().addAll("badge", "badge-" + variant);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    static Label requestBadge(RentalRequestStatus status) {
        String text = status == RentalRequestStatus.OPEN ? "Open for offers" : status.dbValue();
        return badge(text, status.dbValue().toLowerCase(Locale.ROOT));
    }

    /** Offer statuses as students read them. */
    static Label offerBadge(OfferStatus status) {
        String text = switch (status) {
            case OFFERED -> "Waiting for review";
            case APPROVED -> "Approved";
            case CHECKED_IN -> "With the library";
            case RETURNED -> "Returned to you";
            case DECLINED -> "Declined";
            case WITHDRAWN -> "Withdrawn";
            case CANCELLED -> "Cancelled";
        };
        return badge(text, status.dbValue().toLowerCase(Locale.ROOT));
    }

    static Label itemBadge(ItemStatus status) {
        String text = switch (status) {
            case PENDING -> "Pending";
            case AVAILABLE -> "Available";
            case BORROWED -> "Borrowed";
            case OVERDUE -> "Overdue";
            case RETURNED -> "Returned";
            case REJECTED -> "Rejected";
        };
        return badge(text, status.dbValue().toLowerCase(Locale.ROOT));
    }

    /** Students see Active loans as "Borrowed", as in the design. */
    static Label loanBadge(LoanStatus status) {
        return switch (status) {
            case ACTIVE -> badge("Borrowed", "borrowed");
            case OVERDUE -> badge("Overdue", "overdue");
            case RETURNED -> badge("Returned", "returned");
        };
    }

    static Label heading(String text) {
        Label label = new Label(Lang.t(text));
        label.getStyleClass().add("heading");
        return label;
    }

    static Label section(String text) {
        Label label = new Label(Lang.t(text));
        label.getStyleClass().add("section-title");
        return label;
    }

    static Label muted(String text) {
        Label label = new Label(Lang.t(text));
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    /** A full-width message. kind is danger, warning, info or success. */
    static Label banner(String text, String kind) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        Icons icon = switch (kind) {
            case "danger", "warning" -> Icons.ALERT;
            case "success" -> Icons.CHECK;
            default -> Icons.INFO;
        };
        label.setGraphic(icon.node(18));
        label.getStyleClass().addAll("banner", "banner-" + kind);
        return label;
    }

    /** Page title and subtitle on the left, actions on the right. */
    static HBox pageHeader(String title, String subtitle, Node... actions) {
        Label heading = heading(title);
        Label sub = muted(subtitle);
        sub.getStyleClass().add("subheading");
        VBox text = new VBox(4, heading, sub);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(16, text, gap);
        header.getChildren().addAll(actions);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    /** A form field: bold label above the control. */
    static VBox labelled(String text, Node control) {
        return new VBox(6, fieldLabel(text), control);
    }

    static Region grow() {
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        return gap;
    }

    /**
     * A segmented control: one pill per value, exactly one selected. Values that {@code enabled} rejects are shown
     * but disabled. Read the choice from {@code group.getSelectedToggle().getUserData()}.
     */
    static <T> HBox segmented(ToggleGroup group, List<T> values, Function<T, String> text,
                              T selected, Predicate<T> enabled) {
        HBox box = new HBox();
        box.getStyleClass().add("segmented");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        for (T value : values) {
            ToggleButton button = new ToggleButton(text.apply(value));
            button.getStyleClass().add("segment");
            button.setUserData(value);
            button.setToggleGroup(group);
            button.setDisable(!enabled.test(value));
            button.setSelected(value.equals(selected));
            box.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true); // a segmented control always has one choice
            }
        });
        return box;
    }

    /** Applies the app stylesheet to a dialog, which otherwise falls back to the default Modena look. */
    static void style(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(Ui.class.getResource(STYLESHEET).toExternalForm());
        pane.setGraphic(null);
    }

    /** Sizes a table to its rows, so a short table does not show empty rows underneath. */
    static void fitTable(javafx.scene.control.TableView<?> table, int rows) {
        table.setFixedCellSize(48);
        table.setPrefHeight(rows == 0 ? 160 : Math.min(46 + rows * 48, 570)); // 40px header plus borders
    }

    static VBox card(javafx.scene.Node... children) {
        VBox box = new VBox(14, children);
        box.getStyleClass().add("card");
        return box;
    }

    static VBox metric(String label, String value, String tone) {
        Label number = new Label(value);
        number.getStyleClass().addAll("metric-value", "metric-" + tone);
        VBox box = new VBox(4, number, muted(label));
        box.getStyleClass().add("card");
        box.setMinWidth(170);
        HBox.setHgrow(box, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    static HBox row(double spacing, Node... nodes) {
        HBox box = new HBox(spacing, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** A placeholder image, coloured by category, until real photos are attached. */
    static StackPane art(ItemCategory category, double width, double height) {
        Icons icon = switch (category) {
            case CALCULATORS -> Icons.CALCULATOR;
            case LAPTOPS_TABLETS -> Icons.LAPTOP;
            case TEXTBOOKS -> Icons.BOOK;
            case LAB_STUDY_KITS -> Icons.FLASK;
        };
        String style = switch (category) {
            case CALCULATORS -> "art-calc";
            case LAPTOPS_TABLETS -> "art-laptop";
            case TEXTBOOKS -> "art-book";
            case LAB_STUDY_KITS -> "art-kit";
        };
        return sized(icon, style, width, height);
    }

    static StackPane rentalArt(RentalCategory category, double width, double height) {
        Icons icon = switch (category) {
            case TECH -> Icons.LAPTOP;
            case EQUIPMENT -> Icons.BOX;
            case TOOLS -> Icons.FLASK;
            case DISPLAY -> Icons.LAYERS;
            case BOOKS_MEDIA -> Icons.BOOK;
            case OTHER -> Icons.TAG;
        };
        return sized(icon, "art-rental", width, height);
    }

    static StackPane documentArt(boolean college, double width, double height) {
        return sized(Icons.TABLET, college ? "art-doc-college" : "art-doc-k12", width, height);
    }

    private static StackPane sized(Icons icon, String style, double width, double height) {
        double iconSize = Math.max(18, Math.min(56, Math.min(width, height) * 0.38));
        StackPane pane = new StackPane(icon.node(iconSize));
        pane.getStyleClass().addAll("item-art", style);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Please check this");
        style(alert);
        alert.showAndWait();
    }

    static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        style(alert);
        alert.showAndWait();
    }

    /** A modal confirmation. Returns true only when the user presses OK. */
    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        style(alert);
        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    /** Runs a user action. Rule violations show their message. Anything else shows a generic error. */
    static void run(Runnable action) {
        try {
            action.run();
        } catch (ServiceException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            error("Something went wrong. Please try again.");
        }
    }

    static <T> TableColumn<T, String> textColumn(String title, double width, Function<T, String> text) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return column;
    }

    static <T> TableColumn<T, T> nodeColumn(String title, double width, Function<T, Node> node) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        column.setCellFactory(col -> new TableCell<T, T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : node.apply(item));
            }
        });
        return column;
    }

    static VBox column(double spacing, Node... nodes) {
        return new VBox(spacing, nodes);
    }
}
