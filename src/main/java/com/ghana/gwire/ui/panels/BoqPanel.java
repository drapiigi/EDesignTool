package com.ghana.gwire.ui.panels;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.project.Project;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Live Bill of Quantities derived from placed devices (Phase 3 starter).
 * Cable lengths and full calc-driven BOQ arrive in later phases.
 */
public class BoqPanel {

    public record BoqRow(String item, String qty, String unit, String unitCost, String total) {
    }

    private final VBox root;
    private final TableView<BoqRow> table;
    private final ObservableList<BoqRow> rows = FXCollections.observableArrayList();
    private final Label footer;
    private Project project;

    public BoqPanel() {
        Label title = new Label("Bill of Quantities");
        title.getStyleClass().add("panel-title");

        table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No devices yet — place symbols from the library."));
        table.getColumns().add(col("Item", BoqRow::item));
        table.getColumns().add(col("Qty", BoqRow::qty));
        table.getColumns().add(col("Unit", BoqRow::unit));
        table.getColumns().add(col("Unit cost", BoqRow::unitCost));
        table.getColumns().add(col("Total", BoqRow::total));
        table.setFocusTraversable(false);

        footer = new Label("Currency: GHS · placed-device counts (Phase 3)");
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

    public void setProject(Project project) {
        this.project = project;
        refresh();
    }

    public void refresh() {
        rows.clear();
        if (project == null) {
            footer.setText("Currency: GHS · no project");
            return;
        }
        ComponentLibraryService lib = LibraryBootstrap.get();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlacedDevice d : project.floorPlan().devices()) {
            counts.merge(d.componentId(), 1, Integer::sum);
        }
        double grand = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue();
            Optional<ElectricalComponent> comp = lib == null ? Optional.empty() : lib.getById(id);
            String name = comp.map(ElectricalComponent::name).orElse(id);
            String unit = comp.map(ElectricalComponent::unit).orElse("pcs");
            double unitCost = comp.map(ElectricalComponent::unitCostGhs).orElse(0.0);
            double total = unitCost * qty;
            grand += total;
            rows.add(new BoqRow(
                    name,
                    String.valueOf(qty),
                    unit,
                    String.format("%.2f", unitCost),
                    String.format("%.2f", total)
            ));
        }
        footer.setText(String.format(
                "Currency: GHS · %d line(s) · subtotal %.2f · cables by length in Phase 4+",
                rows.size(), grand
        ));
    }

    private static TableColumn<BoqRow, String> col(String title, java.util.function.Function<BoqRow, String> getter) {
        TableColumn<BoqRow, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        return c;
    }
}
