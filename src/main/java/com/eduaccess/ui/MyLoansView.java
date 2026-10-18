package com.eduaccess.ui;

import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.User;
import com.eduaccess.service.Report;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.File;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Screen 7: current loans with fee tracking and the Extend and Return actions, the full loan history, a
 * "Download history" export and the return instructions.
 */
final class MyLoansView {

    private final AppContext ctx;
    private final AppShell shell;

    MyLoansView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        User user = ctx.session.require();
        LocalDate today = LocalDate.now();
        List<LoanDetail> rows = ctx.loans.myLoans(user, today);

        long overdue = rows.stream().filter(d -> d.loan().status() == LoanStatus.OVERDUE).count();
        long dueSoon = rows.stream()
                .filter(d -> d.loan().status() == LoanStatus.ACTIVE)
                .filter(d -> !d.loan().dueDate().isAfter(today.plusDays(2)))
                .count();
        BigDecimal fees = rows.stream().map(this::payable).reduce(BigDecimal.ZERO, BigDecimal::add);

        Button download = new Button("Download history", Icons.UPLOAD.node(16));
        download.getStyleClass().add("button-secondary");
        download.setOnAction(event -> downloadHistory(user, download));
        Button howTo = new Button("How to return", Icons.INFO.node(16));
        howTo.getStyleClass().add("button-ghost");
        howTo.setOnAction(event -> howToReturn());
        VBox page = new VBox(16, Ui.pageHeader("My Loans", "Track what you borrowed, when it is due and any fees.",
                howTo, download));
        if (overdue > 0) {
            page.getChildren().add(Ui.banner(overdue + " item(s) overdue. Please return "
                    + (overdue == 1 ? "it" : "them") + " today to stop further fees.", "danger"));
        }
        page.getChildren().add(new HBox(14,
                Ui.metric("Borrowed now", String.valueOf(rows.size()), "blue"),
                Ui.metric("Due soon", String.valueOf(dueSoon), "amber"),
                Ui.metric("Overdue", String.valueOf(overdue), "red"),
                Ui.metric("Fees to settle", Ui.money(fees), "navy")));

        TableView<LoanDetail> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setPlaceholder(new Label("You have no open loans. Browse the Physical Hub to borrow an item."));
        Ui.fitTable(table, rows.size());
        table.getColumns().addAll(
                Ui.<LoanDetail>textColumn("Item", 240, LoanDetail::itemTitle),
                Ui.<LoanDetail>textColumn("Borrowed", 125, d -> Ui.date(d.loan().borrowDate())),
                Ui.<LoanDetail>textColumn("Due", 125, d -> Ui.date(d.loan().dueDate())),
                Ui.<LoanDetail>textColumn("Time left", 130, d -> Ui.daysText(today, d.loan().dueDate())),
                Ui.<LoanDetail>nodeColumn("Status", 120, d -> Ui.loanBadge(d.loan().status())),
                Ui.<LoanDetail>textColumn("Fee", 100, d -> Ui.money(payable(d))),
                Ui.<LoanDetail>nodeColumn("Action", 230, d -> actions(d, today)));

        int limit = ctx.loans.rules().policy().maxOpenLoans();
        Label limitText = Ui.muted("You can borrow up to " + limit + " items at a time. You have " + rows.size()
                + " now.");
        limitText.getStyleClass().add("small");

        List<LoanDetail> history = ctx.loans.history(user, today).stream()
                .filter(d -> d.loan().status() == LoanStatus.RETURNED).toList();
        TableView<LoanDetail> past = new TableView<>(FXCollections.observableArrayList(history));
        past.setPlaceholder(new Label("Returned loans will appear here."));
        Ui.fitTable(past, Math.min(history.size(), 8));
        past.getColumns().addAll(
                Ui.<LoanDetail>textColumn("Item", 300, LoanDetail::itemTitle),
                Ui.<LoanDetail>textColumn("Borrowed", 130, d -> Ui.date(d.loan().borrowDate())),
                Ui.<LoanDetail>textColumn("Due", 130, d -> Ui.date(d.loan().dueDate())),
                Ui.<LoanDetail>textColumn("Returned", 130, d -> Ui.date(d.loan().returnDate())),
                Ui.<LoanDetail>textColumn("On time", 110, d -> d.loan().returnDate().isAfter(d.loan().dueDate())
                        ? Ui.daysText(d.loan().returnDate(), d.loan().dueDate()) : "Yes"),
                Ui.<LoanDetail>textColumn("Fee", 110, d -> Ui.money(payable(d))));

        Tab current = new Tab("Current (" + rows.size() + ")", new VBox(10, table, limitText));
        Tab returned = new Tab("History (" + history.size() + ")", past);
        TabPane tabs = new TabPane(current, returned);
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        page.getChildren().add(Ui.card(tabs));
        page.getChildren().add(Ui.muted("How fees are calculated: days late × "
                + Ui.money(ctx.loans.rules().policy().overdueFeePerDay())
                + ", counted automatically from the due date. The librarian can waive fees for students in the "
                + "equity program."));
        return page;
    }

    /** Saves every loan of this student, open and returned, as Excel or CSV. */
    private void downloadHistory(User user, Node anchor) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save your loan history");
        chooser.setInitialFileName("my-loan-history-" + LocalDate.now() + ".xlsx");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File file = chooser.showSaveDialog(anchor.getScene().getWindow());
        if (file == null) {
            return;
        }
        Ui.run(() -> {
            Report report = ctx.reports.loanHistory(user);
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                ctx.exporter.writeCsv(report, file.toPath());
            } else {
                ctx.exporter.writeXlsx(report, file.toPath());
            }
            Ui.info("Saved " + report.rows().size() + " loans to " + file.getName() + ".");
        });
    }

    private void howToReturn() {
        BigDecimal perDay = ctx.loans.rules().policy().overdueFeePerDay();
        Ui.info("""
                1. Bring the item and your school ID to the pickup point shown on the item (Library Counter A,                 Library Counter B or the Admin Office).
                2. Staff check the item with you and mark it returned. Press "Return" here as well if you like.
                3. Late items cost %s per day, counted from the due date. Ask the librarian about a waiver if you                 are in the equity program.
                4. Need more time? Use "Extend" before the due date. Each loan can be extended once.""".formatted(
                Ui.money(perDay)));
    }

    private BigDecimal payable(LoanDetail detail) {
        return ctx.loans.rules().payableFee(detail.loan().feeAmount(), detail.loan().feeWaived());
    }

    private Node actions(LoanDetail detail, LocalDate today) {
        Loan loan = detail.loan();
        int days = ctx.loans.rules().policy().extensionDays();

        Button extend = new Button("Extend +" + days + " days");
        extend.getStyleClass().add("button-secondary");
        extend.setDisable(loan.status() != LoanStatus.ACTIVE
                || !ctx.loans.rules().canExtend(today, loan.dueDate(), loan.extensionCount()));
        extend.setOnAction(event -> Ui.run(() -> {
            LocalDate newDue = ctx.loans.extend(loan.id(), LocalDate.now());
            Ui.info("Extended. The new due date is " + Ui.date(newDue) + ".");
            shell.go(Screen.MY_LOANS);
        }));

        Button giveBack = new Button("Return");
        giveBack.getStyleClass().add("button-danger-outline");
        giveBack.setOnAction(event -> {
            boolean confirmed = Ui.confirm("Return this item?", "Please hand \"" + detail.itemTitle()
                    + "\" over at the library counter. The fee is calculated from today's date.");
            if (confirmed) {
                Ui.run(() -> {
                    Loan returned = ctx.loans.returnItem(loan.id(), LocalDate.now());
                    Ui.info(returned.feeAmount().signum() > 0
                            ? "Returned. Overdue fee: " + Ui.money(returned.feeAmount()) + "."
                            : "Returned. No fee.");
                    shell.go(Screen.MY_LOANS);
                });
            }
        });
        return Ui.row(8, extend, giveBack);
    }
}
