package com.eduaccess.ui;

import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.Loan;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.service.InventoryService.InventorySummary;
import java.time.LocalDate;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Admin: approvals, all items, borrowed items (with counter borrows and returns), and overdue fees. */
final class InventoryView {

    private final AppContext ctx;
    private final AppShell shell;

    InventoryView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        LocalDate today = LocalDate.now();
        InventorySummary summary = ctx.inventory.summary();
        List<PhysicalItem> pending = ctx.inventory.pendingApprovals();
        List<LoanDetail> overdue = ctx.loans.overdueLoans(today);

        VBox page = new VBox(20, new VBox(6, Ui.heading("Inventory & Donation Approval"),
                Ui.muted("Review donations, manage the catalog, record borrows and returns, and handle overdue fees.")));
        if (!pending.isEmpty()) {
            page.getChildren().add(Ui.banner(pending.size() + " donation(s) are waiting for your review. "
                    + "Please inspect each item at the counter before approving.", "warning"));
        }
        page.getChildren().add(new HBox(16,
                Ui.metric("Total items", String.valueOf(summary.total()), "navy"),
                Ui.metric("Available", String.valueOf(summary.available()), "teal"),
                Ui.metric("Borrowed", String.valueOf(summary.borrowed()), "amber"),
                Ui.metric("Overdue", String.valueOf(summary.overdue()), "red")));

        TabPane tabs = new TabPane(
                pendingTab(pending),
                allItemsTab(),
                borrowedTab(today),
                overdueTab(overdue, today));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        page.getChildren().addAll(tabs, lifecycleStrip());
        return page;
    }

    private Tab pendingTab(List<PhysicalItem> pending) {
        TableView<PhysicalItem> table = table(pending, "No donations are waiting for review.");
        table.getColumns().addAll(
                Ui.<PhysicalItem>textColumn("Item", 260, this::itemLabel),
                Ui.<PhysicalItem>textColumn("Donor", 170, i -> i.donorName() == null ? "Library stock" : i.donorName()),
                Ui.<PhysicalItem>textColumn("Condition", 110, i -> i.condition().dbValue()),
                Ui.<PhysicalItem>textColumn("Recommended for", 170, i -> Ui.levelText(i.levels())),
                Ui.<PhysicalItem>nodeColumn("Status", 110, i -> Ui.itemBadge(i.status())),
                Ui.<PhysicalItem>nodeColumn("Action", 210, this::approvalButtons));
        return new Tab("Pending approval (" + pending.size() + ")", table);
    }

    private Node approvalButtons(PhysicalItem item) {
        Button approve = new Button("Approve");
        approve.getStyleClass().add("button-teal");
        approve.setOnAction(event -> Ui.run(() -> {
            ctx.inventory.approve(item.id());
            shell.go(Screen.INVENTORY);
        }));
        Button reject = new Button("Reject");
        reject.getStyleClass().add("button-danger-outline");
        reject.setOnAction(event -> {
            if (Ui.confirm("Reject this donation?", "\"" + item.title()
                    + "\" will not be listed. The donor will see it as rejected.")) {
                Ui.run(() -> {
                    ctx.inventory.reject(item.id());
                    shell.go(Screen.INVENTORY);
                });
            }
        });
        return Ui.row(10, approve, reject);
    }

    private Tab allItemsTab() {
        List<PhysicalItem> items = ctx.inventory.allItems();
        TableView<PhysicalItem> table = table(items, "No items in the catalog yet.");
        table.getColumns().addAll(
                Ui.<PhysicalItem>textColumn("Item", 260, this::itemLabel),
                Ui.<PhysicalItem>textColumn("Category", 170, i -> i.category().dbValue()),
                Ui.<PhysicalItem>nodeColumn("Status", 110, i -> Ui.itemBadge(i.status())),
                Ui.<PhysicalItem>textColumn("Pickup point", 150, PhysicalItem::pickupPoint),
                Ui.<PhysicalItem>nodeColumn("Counter", 170, this::checkInButton));
        Button add = new Button("+ Add item");
        add.getStyleClass().add("button-teal");
        add.setOnAction(event -> AdminForms.addPhysicalItem(ctx, () -> shell.go(Screen.INVENTORY)));
        return new Tab("All items (" + items.size() + ")", new VBox(14, rightAligned(add), table));
    }

    private Node checkInButton(PhysicalItem item) {
        if (item.status() != ItemStatus.RETURNED) {
            return Ui.muted("-");
        }
        Button checkIn = new Button("Mark available");
        checkIn.getStyleClass().add("button-teal");
        checkIn.setOnAction(event -> Ui.run(() -> {
            ctx.inventory.checkIn(item.id());
            shell.go(Screen.INVENTORY);
        }));
        return checkIn;
    }

    private Tab borrowedTab(LocalDate today) {
        List<LoanDetail> open = ctx.loans.allLoans(today).stream()
                .filter(detail -> detail.loan().status() != LoanStatus.RETURNED)
                .toList();
        TableView<LoanDetail> table = table(open, "No items are on loan right now.");
        table.getColumns().addAll(
                Ui.<LoanDetail>textColumn("Item", 240, LoanDetail::itemTitle),
                Ui.<LoanDetail>textColumn("Borrower", 230, d -> d.borrowerName() + " (" + d.borrowerSchoolId() + ")"),
                Ui.<LoanDetail>textColumn("Borrowed", 120, d -> Ui.date(d.loan().borrowDate())),
                Ui.<LoanDetail>textColumn("Due", 120, d -> Ui.date(d.loan().dueDate())),
                Ui.<LoanDetail>nodeColumn("Status", 120, d -> Ui.loanBadge(d.loan().status())),
                Ui.<LoanDetail>nodeColumn("Counter", 130, this::returnButton));
        Button record = new Button("+ Record a borrow");
        record.getStyleClass().add("button-teal");
        record.setOnAction(event -> AdminForms.recordBorrow(ctx, () -> shell.go(Screen.INVENTORY)));
        return new Tab("Borrowed items (" + open.size() + ")", new VBox(14, rightAligned(record), table));
    }

    private Node returnButton(LoanDetail detail) {
        Button giveBack = new Button("Return");
        giveBack.getStyleClass().add("button-secondary");
        giveBack.setOnAction(event -> Ui.run(() -> {
            ctx.loans.returnItem(detail.loan().id(), LocalDate.now());
            Ui.info("Returned. The item waits for a counter check before it is available again.");
            shell.go(Screen.INVENTORY);
        }));
        return giveBack;
    }

    private Tab overdueTab(List<LoanDetail> overdue, LocalDate today) {
        TableView<LoanDetail> table = table(overdue, "No overdue loans right now.");
        table.getColumns().addAll(
                Ui.<LoanDetail>textColumn("Item", 240, LoanDetail::itemTitle),
                Ui.<LoanDetail>textColumn("Borrower", 220, d -> d.borrowerName() + " (" + d.borrowerSchoolId() + ")"),
                Ui.<LoanDetail>textColumn("Due", 110, d -> Ui.date(d.loan().dueDate())),
                Ui.<LoanDetail>textColumn("Days late", 100,
                        d -> String.valueOf(ctx.loans.rules().daysOverdue(d.loan().dueDate(), today))),
                Ui.<LoanDetail>textColumn("Fee", 100, d -> Ui.money(ctx.loans.rules()
                        .payableFee(d.loan().feeAmount(), d.loan().feeWaived()))),
                Ui.<LoanDetail>nodeColumn("Equity waiver", 170, this::waiverButton));
        return new Tab("Overdue (" + overdue.size() + ")", table);
    }

    private Node waiverButton(LoanDetail detail) {
        Loan loan = detail.loan();
        Button toggle = new Button(loan.feeWaived() ? "Restore fee" : "Waive fee");
        toggle.getStyleClass().add(loan.feeWaived() ? "button-secondary" : "button-teal");
        toggle.setOnAction(event -> Ui.run(() -> {
            ctx.loans.setFeeWaived(loan.id(), !loan.feeWaived(), LocalDate.now());
            shell.go(Screen.INVENTORY);
        }));
        return toggle;
    }

    private HBox lifecycleStrip() {
        return Ui.row(8,
                Ui.muted("Item lifecycle:"),
                Ui.itemBadge(ItemStatus.AVAILABLE), Ui.muted("→"),
                Ui.itemBadge(ItemStatus.BORROWED), Ui.muted("→"),
                Ui.itemBadge(ItemStatus.OVERDUE), Ui.muted("→"),
                Ui.itemBadge(ItemStatus.RETURNED), Ui.muted("→ counter check →"),
                Ui.itemBadge(ItemStatus.AVAILABLE));
    }

    private String itemLabel(PhysicalItem item) {
        return item.brandModel() == null ? item.title() : item.title() + "  ·  " + item.brandModel();
    }

    private static HBox rightAligned(Node node) {
        HBox bar = new HBox(node);
        bar.setAlignment(Pos.CENTER_RIGHT);
        return bar;
    }

    private static <T> TableView<T> table(List<T> rows, String emptyText) {
        TableView<T> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setPlaceholder(new Label(emptyText));
        // Every tab uses the same height. The tab pane sizes itself to its tallest tab, so a fitted height here
        // would leave empty rows in the other tabs.
        Ui.fitTable(table, 7);
        return table;
    }
}
