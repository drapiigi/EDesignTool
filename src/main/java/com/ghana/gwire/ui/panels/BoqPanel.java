package com.ghana.gwire.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Live Bill of Quantities panel. Phase 1 shows empty schema; data arrives with calculations.
 */
public class BoqPanel {

    private final VBox root;

    public BoqPanel() {
        Label title = new Label("Bill of Quantities");
        title.getStyleClass().add("panel-title");

        TableView<Void> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No items yet — generate or edit a design to populate BOQ."));
        table.getColumns().addAll(
                col("Item", 0.35),
                col("Qty", 0.12),
                col("Unit", 0.12),
                col("Unit cost", 0.20),
                col("Total", 0.21)
        );
        table.setFocusTraversable(false);

        Label footer = new Label("Currency: GHS · costs editable via component library (Phase 3+)");
        footer.getStyleClass().add("panel-footer");

        VBox content = new VBox(8, title, table, footer);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");
        VBox.setVgrow(table, Priority.ALWAYS);

        root = new VBox(content);
        root.getStyleClass().addAll("side-panel", "boq-panel");
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    public VBox getRoot() {
        return root;
    }

    private static TableColumn<Void, String> col(String name, double maxWidth) {
        TableColumn<Void, String> c = new TableColumn<>(name);
        c.setMaxWidth(1f * Integer.MAX_VALUE * maxWidth);
        return c;
    }
}
