package com.ghana.gwire.ui.panels;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Placeholder for the high-performance floor-plan / wiring canvas (Phase 2+).
 */
public class CanvasPlaceholder {

    private final StackPane root;
    private final Label headline;
    private final Label detail;

    public CanvasPlaceholder() {
        headline = new Label("Floor plan canvas");
        headline.getStyleClass().add("canvas-headline");

        detail = new Label(
                "Upload or draw a floor plan here.\n"
                        + "Phase 2: vector rooms/walls · image/PDF import.\n"
                        + "Phase 3+: symbols, wiring routes, AI placement."
        );
        detail.getStyleClass().add("canvas-detail");
        detail.setWrapText(true);
        detail.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, headline, detail);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("canvas-placeholder-inner");

        root = new StackPane(box);
        root.getStyleClass().add("canvas-placeholder");
    }

    public StackPane getRoot() {
        return root;
    }

    public void setHeadline(String text) {
        headline.setText(text);
    }

    public void setDetail(String text) {
        detail.setText(text);
    }
}
