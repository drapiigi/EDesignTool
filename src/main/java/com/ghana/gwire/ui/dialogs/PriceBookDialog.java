package com.ghana.gwire.ui.dialogs;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.components.ElectricalComponent;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Editable catalogue price book: unit costs (GHS) for components in the library.
 *
 * <p>Persists cost edits via {@link ComponentLibraryService#updateCost(String, double)}.
 * Does not modify menus — parent wires the entry point.
 */
public final class PriceBookDialog {

    private static final Logger log = LoggerFactory.getLogger(PriceBookDialog.class);

    private final Stage stage;
    private final ObservableList<PriceRow> allRows = FXCollections.observableArrayList();
    private final FilteredList<PriceRow> filteredRows = new FilteredList<>(allRows, r -> true);
    private final Label statusLabel = new Label();
    private ComponentLibraryService library;

    public PriceBookDialog(Stage owner) {
        stage = new Stage();
        stage.setTitle("Price Book");
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setMinWidth(640);
        stage.setMinHeight(420);

        TextField search = new TextField();
        search.setPromptText("Search id, name, category…");
        search.textProperty().addListener((o, a, q) -> applyFilter(q));

        TableView<PriceRow> table = new TableView<>(filteredRows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No catalogue components loaded."));

        TableColumn<PriceRow, String> idCol = new TableColumn<>("Id");
        idCol.setCellValueFactory(c -> c.getValue().idProp);
        idCol.setEditable(false);

        TableColumn<PriceRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> c.getValue().nameProp);
        nameCol.setEditable(false);

        TableColumn<PriceRow, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> c.getValue().categoryProp);
        catCol.setEditable(false);

        TableColumn<PriceRow, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(c -> c.getValue().unitProp);
        unitCol.setEditable(false);
        unitCol.setMaxWidth(80);
        unitCol.setMinWidth(50);

        TableColumn<PriceRow, String> costCol = new TableColumn<>("Unit cost (GHS)");
        costCol.setCellValueFactory(c -> c.getValue().costProp);
        costCol.setCellFactory(TextFieldTableCell.forTableColumn());
        costCol.setEditable(true);
        costCol.setOnEditCommit(ev -> onCostCommit(ev.getRowValue(), ev.getNewValue()));

        table.getColumns().add(idCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(catCol);
        table.getColumns().add(unitCol);
        table.getColumns().add(costCol);

        statusLabel.getStyleClass().add("panel-footer");
        statusLabel.setWrapText(true);

        Button close = new Button("Save / Close");
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.close());

        Button reload = new Button("Reload");
        reload.setOnAction(e -> loadRows());

        HBox toolbar = new HBox(8, new Label("Filter:"), search, reload);
        HBox.setHgrow(search, Priority.ALWAYS);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        HBox bottom = new HBox(12, statusLabel, close);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        bottom.setPadding(new Insets(8, 0, 0, 0));

        VBox center = new VBox(8, toolbar, table, bottom);
        VBox.setVgrow(table, Priority.ALWAYS);
        center.setPadding(new Insets(12));

        BorderPane root = new BorderPane(center);
        Scene scene = new Scene(root, 780, 520);
        stage.setScene(scene);

        loadRows();
    }

    /** Shows a modal price book owned by {@code owner}. */
    public static void show(Stage owner) {
        PriceBookDialog dialog = new PriceBookDialog(owner);
        dialog.stage.showAndWait();
    }

    /** Shows a modal price book owned by any window. */
    public static void show(Window owner) {
        Stage stageOwner = owner instanceof Stage s ? s : null;
        show(stageOwner);
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private void loadRows() {
        allRows.clear();
        library = resolveLibrary();
        if (library == null) {
            statusLabel.setText("Component library is not available. Costs cannot be edited.");
            return;
        }
        try {
            for (ElectricalComponent c : library.listAll()) {
                allRows.add(PriceRow.from(c));
            }
            statusLabel.setText(allRows.size() + " components · double-click unit cost to edit");
        } catch (RuntimeException ex) {
            log.warn("Price book load failed: {}", ex.getMessage());
            statusLabel.setText("Failed to load catalogue: " + ex.getMessage());
        }
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filteredRows.setPredicate(r -> true);
            return;
        }
        filteredRows.setPredicate(r ->
                r.idProp.get().toLowerCase(Locale.ROOT).contains(q)
                        || r.nameProp.get().toLowerCase(Locale.ROOT).contains(q)
                        || r.categoryProp.get().toLowerCase(Locale.ROOT).contains(q));
    }

    private void onCostCommit(PriceRow row, String raw) {
        if (row == null) {
            return;
        }
        if (library == null) {
            statusLabel.setText("Library not available — cost not saved.");
            row.costProp.set(formatCost(row.lastAcceptedCost));
            return;
        }
        String text = raw == null ? "" : raw.trim().replace(",", "");
        double cost;
        try {
            cost = Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid cost for " + row.idProp.get() + " — enter a number ≥ 0.");
            row.costProp.set(formatCost(row.lastAcceptedCost));
            return;
        }
        if (cost < 0 || Double.isNaN(cost) || Double.isInfinite(cost)) {
            statusLabel.setText("Cost must be ≥ 0 for " + row.idProp.get() + ".");
            row.costProp.set(formatCost(row.lastAcceptedCost));
            return;
        }
        try {
            library.updateCost(row.idProp.get(), cost);
            row.lastAcceptedCost = cost;
            row.costProp.set(formatCost(cost));
            statusLabel.setText("Updated " + row.idProp.get() + " → GHS " + formatCost(cost));
        } catch (RuntimeException ex) {
            log.warn("updateCost failed for {}: {}", row.idProp.get(), ex.getMessage());
            statusLabel.setText("Save failed: " + ex.getMessage());
            row.costProp.set(formatCost(row.lastAcceptedCost));
        }
    }

    private static ComponentLibraryService resolveLibrary() {
        try {
            ComponentLibraryService lib = LibraryBootstrap.get();
            if (lib != null) {
                return lib;
            }
            return LibraryBootstrap.initialize();
        } catch (RuntimeException ex) {
            log.warn("Could not resolve component library: {}", ex.getMessage());
            return null;
        }
    }

    private static String formatCost(double cost) {
        if (Math.rint(cost) == cost && Math.abs(cost) < 1e12) {
            return String.format(Locale.US, "%.0f", cost);
        }
        return String.format(Locale.US, "%.2f", cost);
    }

    /** Mutable table row for price book editing. */
    private static final class PriceRow {
        final SimpleStringProperty idProp;
        final SimpleStringProperty nameProp;
        final SimpleStringProperty categoryProp;
        final SimpleStringProperty unitProp;
        final SimpleStringProperty costProp;
        double lastAcceptedCost;

        private PriceRow(String id, String name, String category, String unit, double cost) {
            this.idProp = new SimpleStringProperty(id);
            this.nameProp = new SimpleStringProperty(name);
            this.categoryProp = new SimpleStringProperty(category);
            this.unitProp = new SimpleStringProperty(unit);
            this.lastAcceptedCost = cost;
            this.costProp = new SimpleStringProperty(formatCost(cost));
        }

        static PriceRow from(ElectricalComponent c) {
            return new PriceRow(
                    c.id(),
                    c.name(),
                    c.category() == null ? "" : c.category().name(),
                    c.unit() == null ? "pcs" : c.unit(),
                    c.unitCostGhs()
            );
        }
    }
}
