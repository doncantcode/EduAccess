package com.eduaccess.ui;

import com.eduaccess.model.GivingType;
import com.eduaccess.model.ItemStatus;
import com.eduaccess.model.PhysicalItem;
import com.eduaccess.model.User;
import com.eduaccess.service.LoanPolicy;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Screen 5: one item, its status tracker and the borrow form. Confirming opens a modal summary first. */
final class ItemDetailView {

    private static final List<ItemStatus> TRACKER = List.of(
            ItemStatus.AVAILABLE, ItemStatus.BORROWED, ItemStatus.OVERDUE, ItemStatus.RETURNED);

    private final AppContext ctx;
    private final AppShell shell;
    private final long itemId;

    ItemDetailView(AppContext ctx, AppShell shell, long itemId) {
        this.ctx = ctx;
        this.shell = shell;
        this.itemId = itemId;
    }

    Node build() {
        User user = ctx.session.require();
        PhysicalItem item = ctx.inventory.find(itemId);

        Button back = new Button("← Back to Physical Hub");
        back.getStyleClass().add("button-secondary");
        back.setOnAction(event -> shell.go(Screen.CATALOG));
        Label crumbs = Ui.muted("Physical Hub  ›  " + item.category().dbValue() + "  ›  " + item.title());

        VBox left = leftColumn(item);
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox right = borrowCard(user, item);
        right.setPrefWidth(360);
        right.setMinWidth(340);
        return new VBox(14, back, crumbs, new HBox(20, left, right));
    }

    private VBox leftColumn(PhysicalItem item) {
        Label title = Ui.heading(item.title());
        title.setWrapText(true);
        Label brand = Ui.muted(item.brandModel() == null ? "" : item.brandModel());
        String giving = item.givingType() == GivingType.DONATE ? "Donated to library" : "Lent by donor";
        HBox badges = Ui.row(8, Ui.itemBadge(item.status()), Ui.badge(giving, "online"));

        GridPane facts = new GridPane();
        facts.setHgap(18);
        facts.setVgap(8);
        fact(facts, 0, "Donated by", item.donorName() == null ? "Library stock" : item.donorName());
        fact(facts, 1, "Condition", item.condition().dbValue());
        fact(facts, 2, "Best for", Ui.levelText(item.levels()));
        fact(facts, 3, "Pickup point", item.pickupPoint());
        fact(facts, 4, "Type of giving", item.givingType() == GivingType.DONATE ? "Donate" : "Lend");

        return new VBox(12, Ui.art(item.category(), 360, 220), title, brand, badges,
                Ui.card(facts), trackerCard(item.status()));
    }

    private static void fact(GridPane grid, int row, String label, String value) {
        Label key = new Label(label);
        key.getStyleClass().add("field-label");
        Label val = new Label(value);
        val.setWrapText(true);
        grid.add(key, 0, row);
        grid.add(val, 1, row);
    }

    private VBox trackerCard(ItemStatus current) {
        HBox steps = new HBox(14);
        for (ItemStatus step : TRACKER) {
            Label label = new Label(step.dbValue());
            label.getStyleClass().add(step == current ? "tracker-current" : "tracker-step");
            steps.getChildren().add(label);
        }
        return Ui.card(Ui.section("Item status tracker"), steps, Ui.muted(trackerNote(current)));
    }

    private static String trackerNote(ItemStatus status) {
        return switch (status) {
            case AVAILABLE -> "This item is ready to borrow. The status updates automatically after pickup and return.";
            case BORROWED -> "This item is currently on loan.";
            case OVERDUE -> "This item is overdue. The borrower owes an overdue fee.";
            case RETURNED -> "This item was returned and is waiting for a counter check.";
            case PENDING, REJECTED -> "This item is not open for borrowing.";
        };
    }

    private VBox borrowCard(User user, PhysicalItem item) {
        LocalDate today = LocalDate.now();
        LoanPolicy policy = ctx.loans.rules().policy();
        boolean available = item.status() == ItemStatus.AVAILABLE;

        ToggleGroup periodGroup = new ToggleGroup();
        HBox periods = new HBox(8);
        List<Integer> options = policy.loanPeriodDays().stream().sorted().toList();
        for (int days : options) {
            ToggleButton option = new ToggleButton(days + " days");
            option.getStyleClass().add("toggle-card");
            option.setToggleGroup(periodGroup);
            option.setUserData(days);
            option.setSelected(days == 7 || (!options.contains(7) && days == options.get(0)));
            HBox.setHgrow(option, Priority.ALWAYS);
            periods.getChildren().add(option);
        }
        periodGroup.selectedToggleProperty().addListener((obs, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true);
            }
        });

        Label dueDate = new Label();
        periodGroup.selectedToggleProperty().addListener((obs, was, now) ->
                dueDate.setText(Ui.date(today.plusDays(selectedDays(periodGroup)))));
        dueDate.setText(Ui.date(today.plusDays(selectedDays(periodGroup))));

        GridPane dates = new GridPane();
        dates.setHgap(18);
        dates.setVgap(6);
        fact(dates, 0, "Borrow date", Ui.date(today));
        Label dueKey = new Label("Due date (automatic)");
        dueKey.getStyleClass().add("field-label");
        dates.add(dueKey, 0, 1);
        dates.add(dueDate, 1, 1);

        Label policyText = Ui.muted("Loan fee: free.\nOverdue fee: " + Ui.money(policy.overdueFeePerDay())
                + " per day after the due date.\nExtensions: " + policy.maxExtensions() + " extension of "
                + policy.extensionDays() + " days, only while the loan is on time.");

        CheckBox agree = new CheckBox("I agree to return this item in good condition by the due date.");
        Button confirm = new Button("Confirm borrow request");
        confirm.getStyleClass().add("button-primary");
        confirm.setMaxWidth(Double.MAX_VALUE);
        agree.selectedProperty().addListener((obs, was, now) -> confirm.setDisable(!available || !now));
        confirm.setDisable(!available || !agree.isSelected());
        confirm.setOnAction(event -> confirmBorrow(user, item, selectedDays(periodGroup)));

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("button-secondary");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(event -> shell.go(Screen.CATALOG));

        Label blocked = Ui.muted(available ? "" : "This item is " + item.status().dbValue().toLowerCase()
                + " right now and cannot be borrowed.");
        return Ui.card(Ui.section("Borrow this item"), Ui.muted("Loan period"), periods, dates, policyText,
                agree, confirm, cancel, blocked);
    }

    /** The modal summary the borrower confirms before the loan is recorded. */
    private void confirmBorrow(User user, PhysicalItem item, int days) {
        LocalDate today = LocalDate.now();
        LocalDate due = today.plusDays(days);
        LoanPolicy policy = ctx.loans.rules().policy();
        String details = item.title() + " for " + days + " days.\n\n"
                + "Borrow date: " + Ui.date(today) + "\n"
                + "Due date: " + Ui.date(due) + "\n"
                + "Loan fee: free. Overdue fee: " + Ui.money(policy.overdueFeePerDay()) + " per day.";
        if (!Ui.confirm("Confirm borrow request", details)) {
            return;
        }
        Ui.run(() -> {
            ctx.loans.borrow(user, itemId, days, LocalDate.now());
            Ui.info("Borrowed. Please return \"" + item.title() + "\" by " + Ui.date(due) + ".");
            shell.go(Screen.MY_LOANS);
        });
    }

    private static int selectedDays(ToggleGroup group) {
        return group.getSelectedToggle() == null ? 7 : (Integer) group.getSelectedToggle().getUserData();
    }
}
