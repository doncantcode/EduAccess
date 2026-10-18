package com.eduaccess.ui;

import com.eduaccess.model.Announcement;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Campus announcements: the student dashboard card, and the staff card with the post and remove actions. */
final class AnnouncementViews {

    private AnnouncementViews() {
    }

    /** What students see. Empty when nothing is showing. */
    static VBox studentCard(AppContext ctx) {
        List<Announcement> showing = ctx.announcements.visible(LocalDate.now());
        VBox card = Ui.card(Ui.row(10, Icons.INFO.node(18), Ui.section("Campus announcements")));
        if (showing.isEmpty()) {
            card.getChildren().add(Ui.muted("No announcements right now."));
        }
        for (Announcement a : showing.stream().limit(3).toList()) {
            card.getChildren().add(entry(a));
        }
        card.getStyleClass().add("announcement-card");
        return card;
    }

    /** The staff card: what is showing now, each with Remove, and the Post announcement button. */
    static VBox staffCard(AppContext ctx, Runnable reload) {
        Button post = new Button("Post announcement", Icons.PLUS.node(16));
        post.getStyleClass().addAll("button-primary", "button-small");
        post.setOnAction(event -> postDialog(ctx, reload));
        VBox card = Ui.card(Ui.row(10, Ui.section("Campus announcements"), Ui.grow(), post),
                Ui.muted("Students see these on their dashboard until the end date."));
        List<Announcement> showing = ctx.announcements.visible(LocalDate.now());
        if (showing.isEmpty()) {
            card.getChildren().add(Ui.muted("Nothing is showing right now."));
        }
        for (Announcement a : showing) {
            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("button-danger-outline", "button-small");
            remove.setOnAction(event -> {
                if (Ui.confirm("Remove \"" + a.title() + "\"?", "Students will no longer see it.")) {
                    Ui.run(() -> {
                        ctx.announcements.remove(ctx.session.require(), a.id());
                        reload.run();
                    });
                }
            });
            Node text = entry(a);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(14, text, remove);
            row.getStyleClass().add("card-flat");
            card.getChildren().add(row);
        }
        return card;
    }

    private static VBox entry(Announcement a) {
        Label title = new Label(a.title());
        title.getStyleClass().add("item-title");
        title.setWrapText(true);
        Label body = new Label(a.body());
        body.setWrapText(true);
        body.getStyleClass().add("small");
        String dates = "Posted " + Ui.date(a.postedOn()) + (a.expiresOn() == null ? "" : "  ·  Until "
                + Ui.date(a.expiresOn())) + "  ·  " + a.postedBy();
        Label meta = Ui.muted(dates);
        meta.getStyleClass().add("small");
        VBox box = new VBox(4, title, body, meta);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static void postDialog(AppContext ctx, Runnable reload) {
        Dialog<ButtonType> dialog = RentalDialogs.dialog("Post announcement",
                "Students see it on their dashboard from today.");
        TextField title = new TextField();
        title.setPromptText("e.g. Donation drive for graduating students");
        TextArea body = new TextArea();
        body.setPromptText("What should students know?");
        body.setPrefRowCount(4);
        body.setWrapText(true);
        CheckBox ends = new CheckBox("Stop showing it after a date");
        DatePicker until = new DatePicker(LocalDate.now().plusDays(14));
        until.disableProperty().bind(ends.selectedProperty().not());
        ends.setSelected(true);
        Label error = RentalDialogs.errorLabel();
        VBox content = new VBox(14, Ui.labelled("Title *", title), Ui.labelled("Message *", body),
                new VBox(8, ends, until), error);
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        ButtonType post = new ButtonType("Post", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, post);
        RentalDialogs.guard(dialog, post, error, () -> ctx.announcements.post(ctx.session.require(),
                title.getText(), body.getText(), ends.isSelected() ? until.getValue() : null, LocalDate.now()));
        title.requestFocus();
        dialog.showAndWait().filter(button -> button == post).ifPresent(button -> reload.run());
    }
}
