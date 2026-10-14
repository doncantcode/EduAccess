package com.eduaccess.ui;

import com.eduaccess.model.EducationLevel;
import com.eduaccess.model.ItemCategory;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.service.ServiceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Shared labels, badges, dialogs and formatting for the screens. */
final class Ui {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private Ui() {
    }

    static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
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

    static Label badge(String text, String variant) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", "badge-" + variant);
        return label;
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
        Label label = new Label(text);
        label.getStyleClass().add("heading");
        return label;
    }

    static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    static Label banner(String text, String kind) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().addAll("banner", "banner-" + kind);
        return label;
    }

    /** Sizes a table to its rows, so a short table does not show empty rows underneath. */
    static void fitTable(javafx.scene.control.TableView<?> table, int rows) {
        table.setFixedCellSize(48);
        table.setPrefHeight(rows == 0 ? 160 : Math.min(34 + rows * 48, 560));
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
        boolean compact = width < 100;
        String text = switch (category) {
            case CALCULATORS -> compact ? "Calc" : "Calculator";
            case LAPTOPS_TABLETS -> "Laptop";
            case TEXTBOOKS -> "Book";
            case LAB_STUDY_KITS -> compact ? "Kit" : "Study kit";
        };
        String style = switch (category) {
            case CALCULATORS -> "art-calc";
            case LAPTOPS_TABLETS -> "art-laptop";
            case TEXTBOOKS -> "art-book";
            case LAB_STUDY_KITS -> "art-kit";
        };
        return sized(new Label(text), style, width, height);
    }

    static StackPane documentArt(boolean college, double width, double height) {
        return sized(new Label("PDF"), college ? "art-doc-college" : "art-doc-k12", width, height);
    }

    private static StackPane sized(Label label, String style, double width, double height) {
        label.getStyleClass().add("art-label");
        StackPane pane = new StackPane(label);
        pane.getStyleClass().addAll("item-art", style);
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Please check this");
        alert.showAndWait();
    }

    static void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** A modal confirmation. Returns true only when the user presses OK. */
    static boolean confirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
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
