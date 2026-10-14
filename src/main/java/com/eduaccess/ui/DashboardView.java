package com.eduaccess.ui;

import com.eduaccess.dao.ItemFilter;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.LoanDetail;
import com.eduaccess.model.LoanStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import com.eduaccess.service.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Screen 3: metrics, reminder banners, the items available for the student's level, and quick actions. */
final class DashboardView {

    private final AppContext ctx;
    private final AppShell shell;

    DashboardView(AppContext ctx, AppShell shell) {
        this.ctx = ctx;
        this.shell = shell;
    }

    Node build() {
        User user = ctx.session.require();
        LocalDate today = LocalDate.now();
        List<LoanDetail> loans = ctx.loans.myLoans(user, today);
        ReportService.DashboardCounts counts = ctx.reports.dashboard(user, today);

        String firstName = user.fullName().strip().split("\\s+")[0];
        Label greeting = Ui.heading("Magandang araw, " + firstName + "!");
        Label subtitle = Ui.muted("Here is what is happening with your learning resources today.");
        Button ask = new Button("Ask the AI Study Assistant");
        ask.getStyleClass().add("button-teal");
        ask.setOnAction(event -> shell.go(Screen.AI));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = Ui.row(12, new VBox(4, greeting, subtitle), gap, ask);

        VBox page = new VBox(18, header);

        List<LoanDetail> overdue = loans.stream().filter(d -> d.loan().status() == LoanStatus.OVERDUE).toList();
        if (!overdue.isEmpty()) {
            BigDecimal owed = loans.stream()
                    .map(d -> ctx.loans.rules().payableFee(d.loan().feeAmount(), d.loan().feeWaived()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            page.getChildren().add(Ui.banner(overdue.size() + " item(s) overdue. Please return "
                    + (overdue.size() == 1 ? "it" : "them") + " today to stop further fees. Fees so far: "
                    + Ui.money(owed) + ".", "danger"));
        }
        loans.stream()
                .filter(d -> d.loan().status() == LoanStatus.ACTIVE)
                .filter(d -> !d.loan().dueDate().isAfter(today.plusDays(2)))
                .forEach(d -> page.getChildren().add(Ui.banner("\"" + d.itemTitle() + "\" is due "
                        + Ui.date(d.loan().dueDate()) + " (" + Ui.daysText(today, d.loan().dueDate())
                        + "). Return it at the library counter.", "warning")));

        HBox metrics = new HBox(14,
                Ui.metric("Active loans", String.valueOf(counts.activeLoans()), "blue"),
                Ui.metric("Due within 2 days", String.valueOf(counts.dueWithinTwoDays()), "amber"),
                Ui.metric("Modules downloaded", String.valueOf(counts.downloadedModules()), "teal"),
                Ui.metric("Items donated", String.valueOf(counts.itemsDonated()), "navy"));
        page.getChildren().add(metrics);

        VBox available = availableCard(user);
        HBox.setHgrow(available, Priority.ALWAYS);
        page.getChildren().add(new HBox(16, available, quickActions()));
        return page;
    }

    private VBox availableCard(User user) {
        List<PhysicalItem> items = ctx.inventory
                .browse(new ItemFilter(List.of(), user.educationLevel(), ItemStatus.AVAILABLE, null))
                .stream().limit(4).toList();
        VBox list = new VBox(10);
        if (items.isEmpty()) {
            list.getChildren().add(Ui.muted("Nothing is available for your level right now."));
        }
        for (PhysicalItem item : items) {
            Label title = new Label(item.title());
            title.getStyleClass().add("item-title");
            Label detail = Ui.muted((item.brandModel() == null ? item.category().dbValue() : item.brandModel())
                    + "  ·  " + Ui.levelText(item.levels()));
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            Button view = new Button("View");
            view.getStyleClass().add("button-secondary");
            view.setOnAction(event -> shell.show(new ItemDetailView(ctx, shell, item.id()).build()));
            list.getChildren().add(Ui.row(12, Ui.art(item.category(), 56, 56), new VBox(2, title, detail),
                    gap, Ui.itemBadge(item.status()), view));
        }
        return Ui.card(Ui.section("Available for your level"),
                Ui.muted("Items from the physical hub that match your education level."), list);
    }

    private VBox quickActions() {
        FlowPane buttons = new FlowPane(10, 10);
        buttons.getChildren().addAll(
                quickButton("Browse items", Screen.CATALOG),
                quickButton("Donate", Screen.DONATE),
                quickButton("E-library", Screen.LIBRARY),
                quickButton("Copy to USB", Screen.LIBRARY));
        VBox card = Ui.card(Ui.section("Quick actions"), buttons);
        card.setMinWidth(300);
        card.setPrefWidth(300);
        return card;
    }

    private Button quickButton(String text, Screen target) {
        Button button = new Button(text);
        button.getStyleClass().add("button-secondary");
        button.setPrefWidth(130);
        button.setOnAction(event -> shell.go(target));
        return button;
    }
}
