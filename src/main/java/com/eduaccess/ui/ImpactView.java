package com.eduaccess.ui;

import com.eduaccess.dao.ReportDAO.Activity;
import com.eduaccess.model.EducationLevel;
import com.eduaccess.service.Report;
import com.eduaccess.service.ReportService;
import com.eduaccess.service.ReportType;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

/**
 * Screen 10: usage for a date range (metrics, most requested subjects, resource use by education level, recent
 * activity) and the export panel: any report as Excel or CSV, filtered by level, or the whole database as one
 * Excel workbook.
 */
final class ImpactView {

    private static final int ACTIVITY_ROWS = 8;

    private final AppContext ctx;
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final VBox body = new VBox(18);
    private final ComboBox<ReportType> reportType = new ComboBox<>();
    private final ComboBox<EducationLevel> level = new ComboBox<>();
    private final Label exportStatus = new Label();

    ImpactView(AppContext ctx) {
        this.ctx = ctx;
    }

    Node build() {
        from.setValue(LocalDate.now().minusDays(90));
        to.setValue(LocalDate.now());
        from.setPrefWidth(150);
        to.setPrefWidth(150);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-secondary");
        refresh.setOnAction(event -> render());
        from.valueProperty().addListener((obs, was, now) -> render());
        to.valueProperty().addListener((obs, was, now) -> render());
        HBox range = Ui.row(8, Icons.CALENDAR.node(18), from, Ui.muted("to"), to, refresh);

        Node header = Ui.pageHeader("Campus Impact Dashboard",
                "Physical loans, digital downloads and rentals across every education level.", range);
        configureExportControls();
        render();
        return new VBox(18, header, body);
    }

    private void render() {
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        if (start == null || end == null || end.isBefore(start)) {
            body.getChildren().setAll(Ui.banner("Choose a start date on or before the end date.", "warning"));
            return;
        }
        Ui.run(() -> {
            ReportService.ImpactSummary summary = ctx.reports.impact(start, end);
            HBox metrics = new HBox(14,
                    Ui.metric("Physical loans", String.valueOf(summary.physicalLoans()), "blue"),
                    Ui.metric("Digital downloads", String.valueOf(summary.digitalDownloads()), "teal"),
                    Ui.metric("Active users", String.valueOf(summary.activeUsers()), "navy"),
                    Ui.metric("Overdue items", String.valueOf(summary.overdueItems()), "red"));

            VBox subjects = subjectCard(summary);
            VBox levels = levelCard(ctx.reports.usageByLevel(start, end));
            VBox export = exportCard();
            HBox.setHgrow(subjects, Priority.ALWAYS);
            HBox.setHgrow(levels, Priority.ALWAYS);
            subjects.setPrefWidth(320);
            levels.setPrefWidth(320);
            export.setPrefWidth(310);
            export.setMinWidth(300);
            HBox row = new HBox(16, subjects, levels, export);

            body.getChildren().setAll(metrics, row, activityCard());
        });
    }

    // ----- Charts ----------------------------------------------------------------------------------------------

    /** Horizontal bars, as in the design: subject, bar, count. */
    private VBox subjectCard(ReportService.ImpactSummary summary) {
        VBox card = Ui.card(Ui.section("Most requested subjects"));
        if (summary.topSubjects().isEmpty()) {
            card.getChildren().add(Ui.muted("No digital downloads in this date range yet."));
            return card;
        }
        int max = summary.topSubjects().values().stream().mapToInt(Integer::intValue).max().orElse(1);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        int row = 0;
        for (Map.Entry<String, Integer> entry : summary.topSubjects().entrySet()) {
            Label name = new Label(entry.getKey());
            name.setMinWidth(Region.USE_PREF_SIZE);
            ProgressBar bar = new ProgressBar((double) entry.getValue() / max);
            bar.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(bar, Priority.ALWAYS);
            Label count = new Label(String.valueOf(entry.getValue()));
            count.getStyleClass().add("info-value");
            grid.addRow(row++, name, bar, count);
        }
        card.getChildren().add(grid);
        return card;
    }

    /** Stacked bars per level: physical loans (blue) and digital downloads (teal). */
    private VBox levelCard(Map<String, int[]> usage) {
        CategoryAxis levels = new CategoryAxis();
        NumberAxis count = new NumberAxis();
        count.setMinorTickVisible(false);
        count.setTickUnit(1);
        StackedBarChart<String, Number> chart = new StackedBarChart<>(levels, count);
        chart.setAnimated(false);
        chart.setPrefHeight(230);
        chart.setCategoryGap(24);
        XYChart.Series<String, Number> physical = new XYChart.Series<>();
        physical.setName("Physical loans");
        XYChart.Series<String, Number> digital = new XYChart.Series<>();
        digital.setName("Digital downloads");
        usage.forEach((level, counts) -> {
            physical.getData().add(new XYChart.Data<>(level, counts[0]));
            digital.getData().add(new XYChart.Data<>(level, counts[1]));
        });
        chart.getData().add(physical);
        chart.getData().add(digital);
        int total = usage.values().stream().mapToInt(c -> c[0] + c[1]).sum();
        VBox card = Ui.card(Ui.section("Resource use by education level"), chart);
        if (total == 0) {
            card.getChildren().add(Ui.muted("No loans or downloads in this date range yet."));
        }
        return card;
    }

    private VBox activityCard() {
        List<Activity> activity = ctx.reports.recentActivity(ACTIVITY_ROWS);
        VBox list = new VBox(0);
        if (activity.isEmpty()) {
            list.getChildren().add(Ui.muted("Nothing has happened yet."));
        }
        for (Activity a : activity) {
            Label when = Ui.muted(shortTime(a.when()));
            when.setMinWidth(130);
            when.getStyleClass().add("small");
            Label what = new Label(a.who() + " " + a.what());
            what.setWrapText(true);
            HBox.setHgrow(what, Priority.ALWAYS);
            what.setMaxWidth(Double.MAX_VALUE);
            Label levelText = Ui.muted(a.level() == null ? "Staff" : levelName(a.level()));
            levelText.setMinWidth(110);
            HBox row = Ui.row(14, when, what, levelText, Ui.badge(a.kind(), badgeTone(a.kind())));
            row.getStyleClass().add("activity-row");
            list.getChildren().add(row);
        }
        return Ui.card(Ui.section("Recent activity"), list);
    }

    private static String shortTime(String timestamp) {
        if (timestamp == null) {
            return "";
        }
        try {
            LocalDate day = LocalDate.parse(timestamp.substring(0, 10));
            String time = timestamp.length() >= 16 ? "  " + timestamp.substring(11, 16) : "";
            return Ui.date(day) + time;
        } catch (RuntimeException e) {
            return timestamp;
        }
    }

    private static String levelName(String level) {
        return switch (level) {
            case "JHS" -> "Junior High";
            case "SHS" -> "Senior High";
            default -> level;
        };
    }

    private static String badgeTone(String kind) {
        return switch (kind) {
            case "Borrowed" -> "borrowed";
            case "Returned" -> "returned";
            case "Download" -> "cached";
            case "Donated" -> "available";
            default -> "offered";
        };
    }

    // ----- Export ----------------------------------------------------------------------------------------------

    private void configureExportControls() {
        reportType.getItems().setAll(ReportType.values());
        reportType.setValue(ReportType.BORROWING_LOG);
        reportType.setMaxWidth(Double.MAX_VALUE);
        reportType.setConverter(new StringConverter<>() {
            @Override
            public String toString(ReportType type) {
                return type == null ? "" : type.label();
            }

            @Override
            public ReportType fromString(String text) {
                return null;
            }
        });
        level.getItems().add(null);
        level.getItems().addAll(EducationLevel.values());
        level.setMaxWidth(Double.MAX_VALUE);
        level.setConverter(new StringConverter<>() {
            @Override
            public String toString(EducationLevel value) {
                return value == null ? "All levels" : levelName(value.dbValue());
            }

            @Override
            public EducationLevel fromString(String text) {
                return null;
            }
        });
        reportType.valueProperty().addListener((obs, was, now) -> {
            level.setDisable(now == null || !now.levelFilter());
            exportStatus.setText("");
        });
        exportStatus.setWrapText(true);
        exportStatus.getStyleClass().addAll("field-hint");
    }

    private VBox exportCard() {
        Button excel = new Button("Export to Excel (.xlsx)", Icons.UPLOAD.node(16));
        excel.getStyleClass().addAll("button-teal", "button-block");
        excel.setOnAction(event -> exportOne("xlsx"));
        Button csv = new Button("Export as CSV");
        csv.getStyleClass().addAll("button-secondary", "button-block");
        csv.setOnAction(event -> exportOne("csv"));

        Label fullTitle = new Label("Full database");
        fullTitle.getStyleClass().add("field-label");
        Label fullHint = new Label("Every table in one Excel workbook, one sheet each. Passwords and guardian "
                + "contact details are never exported.");
        fullHint.getStyleClass().add("field-hint");
        fullHint.setWrapText(true);
        Button full = new Button("Export full database (.xlsx)", Icons.LAYERS.node(16));
        full.getStyleClass().addAll("button-primary", "button-block");
        full.setOnAction(event -> exportDatabase());

        Region line = new Region();
        line.getStyleClass().add("divider");
        VBox card = Ui.card(Ui.section("Export reports"),
                Ui.labelled("Report type", reportType),
                Ui.labelled("Education level", level),
                new VBox(8, excel, csv),
                line,
                new VBox(6, fullTitle, fullHint, full),
                exportStatus);
        card.getStyleClass().add("export-card");
        return card;
    }

    private void exportOne(String extension) {
        ReportType type = reportType.getValue();
        Report report = ctx.reports.report(type, level.isDisabled() ? null : level.getValue());
        File file = chooseFile(report.name(), extension);
        if (file == null) {
            return;
        }
        Ui.run(() -> {
            if (extension.equals("xlsx")) {
                ctx.exporter.writeXlsx(report, file.toPath());
            } else {
                ctx.exporter.writeCsv(report, file.toPath());
            }
            exportStatus.setText("Saved " + report.rows().size() + " rows to " + file.getName() + ".");
        });
    }

    private void exportDatabase() {
        File file = chooseFile("EduAccess database", "xlsx");
        if (file == null) {
            return;
        }
        Ui.run(() -> {
            List<Report> all = ctx.reports.fullDatabase();
            Path target = file.toPath();
            ctx.exporter.writeWorkbook(all, "EduAccess database export", target);
            int rows = all.stream().mapToInt(r -> r.rows().size()).sum();
            exportStatus.setText("Saved " + all.size() + " sheets (" + rows + " rows) to " + file.getName() + ".");
        });
    }

    private File chooseFile(String name, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + name);
        chooser.setInitialFileName(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                + "-" + LocalDate.now() + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                extension.equals("xlsx") ? "Excel workbook" : "CSV file", "*." + extension));
        return chooser.showSaveDialog(body.getScene().getWindow());
    }
}
