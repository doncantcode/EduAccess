package com.eduaccess.ui;

import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Screen 7: active loans with fee tracking, the Extend and Return actions. */
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

        VBox page = new VBox(16, new VBox(4, Ui.heading("My Loans"),
                Ui.muted("Track what you borrowed, when it is due and any fees.")));
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
                Ui.<LoanDetail>textColumn("Item", 260, LoanDetail::itemTitle),
                Ui.<LoanDetail>textColumn("Borrowed", 110, d -> Ui.date(d.loan().borrowDate())),
                Ui.<LoanDetail>textColumn("Due", 110, d -> Ui.date(d.loan().dueDate())),
                Ui.<LoanDetail>textColumn("Time left", 130, d -> Ui.daysText(today, d.loan().dueDate())),
                Ui.<LoanDetail>nodeColumn("Status", 120, d -> Ui.loanBadge(d.loan().status())),
                Ui.<LoanDetail>textColumn("Fee", 100, d -> Ui.money(payable(d))),
                Ui.<LoanDetail>nodeColumn("Action", 230, d -> actions(d, today)));

        page.getChildren().add(Ui.card(Ui.section("Current loans"), table));
        page.getChildren().add(Ui.muted("How fees are calculated: days late × "
                + Ui.money(ctx.loans.rules().policy().overdueFeePerDay())
                + ", counted automatically from the due date. The librarian can waive fees for students in the "
                + "equity program."));
        return page;
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
