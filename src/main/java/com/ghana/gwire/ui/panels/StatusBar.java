package com.ghana.gwire.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Bottom status strip for messages and standards context.
 */
public class StatusBar {

    private final HBox root;
    private final Label message;
    private final Label secondary;

    public StatusBar() {
        message = new Label();
        message.getStyleClass().add("status-message");

        secondary = new Label();
        secondary.getStyleClass().add("status-secondary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        root = new HBox(12, message, spacer, secondary);
        root.setPadding(new Insets(6, 12, 6, 12));
        root.getStyleClass().add("status-bar");
    }

    public HBox getRoot() {
        return root;
    }

    public void setMessage(String text) {
        message.setText(text);
    }

    public void setSecondary(String text) {
        secondary.setText(text);
    }
}
