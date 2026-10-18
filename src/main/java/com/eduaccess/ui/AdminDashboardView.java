package com.eduaccess.ui;

import com.eduaccess.service.InventoryService.InventorySummary;
import java.time.LocalDate;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** The library admin's home screen: stock and loan numbers, the to-do banners, and the quick actions. */
final class AdminDashboardView {

    private final AppContext ctx;
    private final AppShell shell;

    AdminDashboardView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        LocalDate today = LocalDate.now();
        InventorySummary summary = ctx.inventory.summary();
        int pending = ctx.inventory.pendingApprovals().size();
        int overdue = ctx.loans.overdueLoans(today).size();

        VBox page = new VBox(20, new VBox(6, Ui.heading("Library Dashboard"),
                Ui.muted("Stock, borrowed items and approvals at a glance.")));
        if (pending > 0) {
            page.getChildren().add(Ui.banner(pending + " donation(s) are waiting for your review. "
                    + "Inspect each item at the counter before approving.", "warning"));
        }
        if (overdue > 0) {
            page.getChildren().add(Ui.banner(overdue + " loan(s) are overdue. Review them in Inventory & Approval.",
                    "danger"));
        }
        page.getChildren().add(new HBox(16,
                Ui.metric("Total items", String.valueOf(summary.total()), "navy"),
                Ui.metric("Available", String.valueOf(summary.available()), "teal"),
                Ui.metric("Borrowed", String.valueOf(summary.borrowed()), "amber"),
                Ui.metric("Overdue", String.valueOf(summary.overdue()), "red"),
                Ui.metric("Waiting for approval", String.valueOf(pending), "blue")));

        FlowPane actions = new FlowPane(12, 12);
        actions.getChildren().addAll(
                action("+ Add physical item", "button-teal",
                        () -> AdminForms.addPhysicalItem(ctx, () -> shell.go(Screen.DASHBOARD))),
                action("+ Add digital resource", "button-teal",
                        () -> AdminForms.addDigitalResource(ctx, () -> shell.go(Screen.DASHBOARD))),
                action("+ Record a borrow", "button-teal",
                        () -> AdminForms.recordBorrow(ctx, () -> shell.go(Screen.DASHBOARD))),
                action("Review donations", "button-secondary", () -> shell.go(Screen.INVENTORY)));
        page.getChildren().add(Ui.card(Ui.section("Quick actions"), actions));
        page.getChildren().add(AnnouncementViews.staffCard(ctx, () -> shell.go(Screen.DASHBOARD)));
        return page;
    }

    private static Button action(String text, String style, Runnable onClick) {
        Button button = new Button(text);
        button.getStyleClass().add(style);
        button.setMinWidth(200);
        button.setOnAction(event -> onClick.run());
        return button;
    }
}
