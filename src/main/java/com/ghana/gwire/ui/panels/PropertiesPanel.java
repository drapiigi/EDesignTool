package com.ghana.gwire.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Selection / project properties sidebar (populated in later phases).
 */
public class PropertiesPanel {

    private final VBox root;

    public PropertiesPanel() {
        Label title = new Label("Properties");
        title.getStyleClass().add("panel-title");

        Label body = new Label(
                "Project settings and selected-component properties will appear here.\n\n"
                        + "Defaults (Ghana):\n"
                        + "• Supply: 230 V / 50 Hz single-phase\n"
                        + "• Earthing: TN-S / TT per L.I. 2008 practice\n"
                        + "• Diversity factors: Energy Commission guidance"
        );
        body.getStyleClass().add("panel-body");
        body.setWrapText(true);

        VBox content = new VBox(10, title, body);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("panel-scroll");

        root = new VBox(scroll);
        root.getStyleClass().addAll("side-panel", "properties-panel");
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
    }

    public VBox getRoot() {
        return root;
    }
}
