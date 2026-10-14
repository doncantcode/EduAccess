package com.eduaccess.ui;

import com.eduaccess.service.Report;
import com.eduaccess.service.ReportService;
import java.io.File;
import java.time.LocalDate;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Screen 10: usage metrics for a date range, the most requested subjects, and CSV or Excel export. */
final class ImpactView {

    private final AppContext ctx;
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final VBox body = new VBox(16);

    ImpactView(AppContext ctx) {
        this.ctx = ctx;
    }

    Node build() {
        from.setValue(LocalDate.now().minusDays(90));
        to.setValue(LocalDate.now());
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("button-primary");
        refresh.setOnAction(event -> render());
        HBox range = Ui.row(10, new Label("From"), from, new Label("To"), to, refresh);

        VBox header = new VBox(4, Ui.heading("Campus Impact Dashboard"),
                Ui.muted("Usage of physical loans, digital downloads and overdue items."));
        VBox page = new VBox(14, header, range, body);
        render();
        return page;
    }

    private void render() {
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        if (start == null || end == null || end.isBefore(start)) {
            Ui.error("Choose a valid date range.");
            return;
        }
        Ui.run(() -> {
            ReportService.ImpactSummary summary = ctx.reports.impact(start, end);
            body.getChildren().setAll(
                    new HBox(14,
                            Ui.metric("Physical loans", String.valueOf(summary.physicalLoans()), "blue"),
                            Ui.metric("Digital downloads", String.valueOf(summary.digitalDownloads()), "teal"),
                            Ui.metric("Active users", String.valueOf(summary.activeUsers()), "navy"),
                            Ui.metric("Overdue items", String.valueOf(summary.overdueItems()), "red")),
                    subjectCard(summary),
                    exportCard());
        });
    }

    private VBox subjectCard(ReportService.ImpactSummary summary) {
        VBox card = Ui.card(Ui.section("Most requested subjects"));
        if (summary.topSubjects().isEmpty()) {
            card.getChildren().add(Ui.muted("No digital downloads in this date range yet."));
            return card;
        }
        CategoryAxis subjects = new CategoryAxis();
        NumberAxis requests = new NumberAxis();
        requests.setLabel("Downloads");
        requests.setMinorTickVisible(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        summary.topSubjects().forEach((subject, count) -> series.getData().add(new XYChart.Data<>(subject, count)));
        BarChart<String, Number> chart = new BarChart<>(subjects, requests);
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);
        chart.getData().add(series);
        card.getChildren().add(chart);
        return card;
    }

    private VBox exportCard() {
        return Ui.card(Ui.section("Export reports"),
                Ui.muted("Exports include every record, not only the date range above."),
                Ui.row(10,
                        exportButton("Borrowing log (.xlsx)", false, "xlsx"),
                        exportButton("Borrowing log (.csv)", false, "csv")),
                Ui.row(10,
                        exportButton("Inventory (.xlsx)", true, "xlsx"),
                        exportButton("Inventory (.csv)", true, "csv")));
    }

    private Button exportButton(String text, boolean inventory, String extension) {
        Button button = new Button(text);
        button.getStyleClass().add(extension.equals("xlsx") ? "button-teal" : "button-secondary");
        button.setOnAction(event -> export(inventory, extension));
        return button;
    }

    private void export(boolean inventory, String extension) {
        Report report = inventory ? ctx.reports.inventoryReport() : ctx.reports.borrowingLogReport();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + report.name());
        chooser.setInitialFileName(report.name().toLowerCase(Locale.ROOT).replace(' ', '-')
                + "-" + LocalDate.now() + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extension.toUpperCase(Locale.ROOT),
                "*." + extension));
        File file = chooser.showSaveDialog(body.getScene().getWindow());
        if (file == null) {
            return;
        }
        Ui.run(() -> {
            if (extension.equals("xlsx")) {
                ctx.exporter.writeXlsx(report, file.toPath());
            } else {
                ctx.exporter.writeCsv(report, file.toPath());
            }
            Ui.info("Saved " + report.rows().size() + " rows to " + file.getName() + ".");
        });
    }
}
