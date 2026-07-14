package com.ghana.gwire.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Bottom status strip: message, CAD command line (Phase 15), standards context.
 */
public class StatusBar {

    private final VBox root;
    private final Label message;
    private final Label secondary;
    private final TextField commandField;
    private Consumer<String> commandHandler = s -> {
    };

    public StatusBar() {
        message = new Label();
        message.getStyleClass().add("status-message");

        secondary = new Label();
        secondary.getStyleClass().add("status-secondary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox top = new HBox(12, message, spacer, secondary);
        top.setPadding(new Insets(4, 12, 2, 12));
        top.setAlignment(Pos.CENTER_LEFT);

        Label cmdLabel = new Label("Cmd:");
        cmdLabel.getStyleClass().add("status-message");
        commandField = new TextField();
        commandField.setPromptText("LINE · 3500 · 3.5m · ORTHO ON · HELP");
        commandField.getStyleClass().add("cad-command-field");
        HBox.setHgrow(commandField, Priority.ALWAYS);
        commandField.setOnAction(e -> {
            String text = commandField.getText();
            commandHandler.accept(text == null ? "" : text);
            commandField.clear();
        });

        HBox cmdRow = new HBox(8, cmdLabel, commandField);
        cmdRow.setPadding(new Insets(0, 12, 6, 12));
        cmdRow.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(top, cmdRow);
        root.getStyleClass().add("status-bar");
    }

    public VBox getRoot() {
        return root;
    }

    public void setMessage(String text) {
        message.setText(text);
    }

    public void setSecondary(String text) {
        secondary.setText(text);
    }

    public void setCommandHandler(Consumer<String> handler) {
        this.commandHandler = handler == null ? s -> {
        } : handler;
    }

    public void focusCommandLine() {
        commandField.requestFocus();
    }

    public TextField getCommandField() {
        return commandField;
    }
}
