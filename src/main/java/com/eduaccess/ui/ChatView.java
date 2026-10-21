package com.eduaccess.ui;

import com.eduaccess.model.PeerMessage;
import com.eduaccess.model.StudentListing;
import com.eduaccess.model.User;
import com.eduaccess.service.ServiceException;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Direct chat thread between students regarding a marketplace listing. */
public final class ChatView {

    private final AppContext ctx;
    private final AppShell shell;
    private final long listingId;

    public ChatView(AppContext ctx, AppShell shell, long listingId) {
        this.ctx = ctx;
        this.shell = shell;
        this.listingId = listingId;
    }

    public Node build() {
        User user = ctx.session.require();
        StudentListing listing = ctx.marketplace.getListing(listingId);

        VBox page = new VBox(16);
        page.setFillWidth(true);

        Button back = new Button("← Back to Listing");
        back.getStyleClass().add("button-secondary");
        back.setOnAction(event -> shell.show(new ListingDetailView(ctx, shell, listingId).build()));

        Label title = Ui.heading("Chat: " + listing.title());
        Label subtitle = Ui.muted("Discuss pickup, condition, or details directly with the student.");

        VBox messageList = new VBox(10);
        messageList.setPadding(new Insets(10));
        messageList.getStyleClass().add("chat-thread");

        List<PeerMessage> messages = ctx.marketplace.thread(listingId, user);
        for (PeerMessage msg : messages) {
            User sender = ctx.marketplace.getUserProfile(msg.senderId());
            boolean mine = sender != null && sender.id() == user.id();

            VBox bubble = new VBox(4);
            bubble.getStyleClass().add(mine ? "chat-bubble-mine" : "chat-bubble");
            bubble.setMaxWidth(480);
            bubble.setPadding(new Insets(10, 14, 10, 14));

            Label senderLbl = Ui.muted(sender != null ? sender.fullName() : "Student");
            senderLbl.setStyle("-fx-font-size: 11px;");
            Label body = new Label(msg.body());
            body.setWrapText(true);
            Label time = Ui.muted(msg.sentAt());
            time.setStyle("-fx-font-size: 10px;");

            bubble.getChildren().addAll(senderLbl, body, time);

            HBox row = new HBox(bubble);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageList.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(messageList);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);

        TextField input = new TextField();
        input.setPromptText("Type your message...");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button("Send");
        send.getStyleClass().add("button-primary");
        Runnable onSend = () -> {
            try {
                ctx.marketplace.sendMessage(user, listingId, input.getText());
                input.clear();
                shell.show(build());
            } catch (ServiceException e) {
                Ui.error(e.getMessage());
            }
        };
        send.setOnAction(event -> onSend.run());
        input.setOnAction(event -> onSend.run());

        HBox inputRow = new HBox(10, input, send);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        page.getChildren().addAll(back, title, subtitle, scroll, inputRow);
        return page;
    }
}
