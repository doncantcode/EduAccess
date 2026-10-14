package com.eduaccess.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Screen 9 placeholder. The AI Study Assistant is deliberately not built in this phase. */
final class AiPlaceholderView {

    Node build() {
        Label title = Ui.heading("AI Study Assistant");
        Label message = new Label("Feature not available for now");
        message.getStyleClass().add("placeholder-message");
        Label note = Ui.muted("This feature is not part of this release. The rest of EduAccess works as usual.");
        VBox box = new VBox(12, title, message, note);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(90, 40, 90, 40));
        return box;
    }
}
