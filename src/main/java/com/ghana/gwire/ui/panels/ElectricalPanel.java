package com.ghana.gwire.ui.panels;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.ValidationIssue;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.electrical.ConsumerUnit;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.electrical.CircuitMaterializer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Phase 14: circuits, consumer unit ways, cable schedule, checklist workflow.
 */
public class ElectricalPanel {

    private final VBox root;
    private final Label summaryLabel;
    private final TableView<Circuit> circuitTable;
    private final ObservableList<Circuit> circuitRows = FXCollections.observableArrayList();
    private final ListView<String> wayList;
    private final ObservableList<String> wayRows = FXCollections.observableArrayList();
    private final TableView<Circuit> cableTable;
    private final ObservableList<Circuit> cableRows = FXCollections.observableArrayList();
    private final ListView<ChecklistRow> checklistView;
    private final ObservableList<ChecklistRow> checklistRows = FXCollections.observableArrayList();
    private final TextField incomerField = new TextField();
    private final TextField rcdField = new TextField();
    private final TextField waysField = new TextField();

    private Project project;
    private Consumer<String> statusSink = s -> {
    };
    private Runnable modelChanged = () -> {
    };

    public ElectricalPanel() {
        Label title = new Label("Circuits & CU");
        title.getStyleClass().add("panel-title");

        summaryLabel = new Label("Recalculate loads to build circuits and the consumer unit.");
        summaryLabel.getStyleClass().add("panel-body");
        summaryLabel.setWrapText(true);

        Button rebuild = new Button("Rebuild circuits from plan");
        rebuild.setOnAction(e -> rebuildCircuits());
        rebuild.getStyleClass().add("tool-action");

        circuitTable = new TableView<>(circuitRows);
        circuitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        circuitTable.setPrefHeight(120);
        circuitTable.getColumns().add(col("Way", c -> c.wayNumber() > 0 ? String.valueOf(c.wayNumber()) : "-"));
        circuitTable.getColumns().add(col("Circuit", Circuit::name));
        circuitTable.getColumns().add(col("Kind", c -> c.kind().name()));
        circuitTable.getColumns().add(col("Dev", c -> String.valueOf(c.deviceIds().size())));
        circuitTable.getColumns().add(col("MCB", c -> c.breakerA() > 0
                ? String.format(Locale.ROOT, "%.0fA", c.breakerA()) : "-"));
        circuitTable.setPlaceholder(new Label("No circuits yet"));

        // CU tab content
        incomerField.setPromptText("Incomer A");
        rcdField.setPromptText("RCD description");
        waysField.setPromptText("Ways");
        Button applyCu = new Button("Apply CU");
        applyCu.setOnAction(e -> applyCuSettings());
        HBox cuFields = new HBox(6, new Label("Incomer"), incomerField,
                new Label("Ways"), waysField);
        VBox cuBox = new VBox(6,
                new Label("Consumer unit"),
                cuFields,
                new Label("RCD"),
                rcdField,
                applyCu,
                wayList = new ListView<>(wayRows)
        );
        wayList.setPrefHeight(140);
        wayList.setPlaceholder(new Label("No CU ways"));

        // Cable schedule
        cableTable = new TableView<>(cableRows);
        cableTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cableTable.setPrefHeight(140);
        cableTable.getColumns().add(col("Circuit", Circuit::name));
        cableTable.getColumns().add(col("Cable", c -> c.cableSize().isBlank() ? "-" : c.cableSize()));
        cableTable.getColumns().add(col("L (m)", c -> String.format(Locale.ROOT, "%.1f", c.estimatedLengthM())));
        cableTable.getColumns().add(col("MCB", c -> c.breakerA() > 0
                ? String.format(Locale.ROOT, "%.0fA", c.breakerA()) : "-"));
        cableTable.setPlaceholder(new Label("No cable runs"));

        // Checklist
        checklistView = new ListView<>(checklistRows);
        checklistView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ChecklistRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                CheckBox cb = new CheckBox(item.label());
                cb.setSelected(item.reviewed());
                cb.setWrapText(true);
                cb.selectedProperty().addListener((o, a, b) -> {
                    if (project != null) {
                        project.checklistReview().setReviewed(item.code(), b, "");
                        modelChanged.run();
                        statusSink.accept(b ? "Reviewed " + item.code() : "Unchecked " + item.code());
                    }
                });
                setGraphic(cb);
            }
        });
        checklistView.setPrefHeight(140);
        checklistView.setPlaceholder(new Label("Recalculate to populate issues"));

        TabPane tabs = new TabPane(
                tab("Circuits", new VBox(6, rebuild, circuitTable)),
                tab("CU board", cuBox),
                tab("Cables", cableTable),
                tab("Checklist", checklistView)
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox content = new VBox(8, title, summaryLabel, tabs);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        root = new VBox(content);
        root.getStyleClass().addAll("side-panel", "electrical-panel");
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private static TableColumn<Circuit, String> col(String title, java.util.function.Function<Circuit, String> fn) {
        TableColumn<Circuit, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(fn.apply(cd.getValue())));
        return c;
    }

    public VBox getRoot() {
        return root;
    }

    public void setProject(Project project) {
        this.project = project;
        refresh();
    }

    public void setStatusSink(Consumer<String> statusSink) {
        this.statusSink = statusSink == null ? s -> {
        } : statusSink;
    }

    public void setModelChanged(Runnable modelChanged) {
        this.modelChanged = modelChanged == null ? () -> {
        } : modelChanged;
    }

    public void refresh() {
        circuitRows.clear();
        cableRows.clear();
        wayRows.clear();
        checklistRows.clear();
        if (project == null) {
            summaryLabel.setText("No project.");
            return;
        }
        circuitRows.addAll(project.circuits());
        cableRows.addAll(project.circuits());
        ConsumerUnit cu = project.consumerUnit();
        if (cu != null) {
            incomerField.setText(String.format(Locale.ROOT, "%.0f", cu.incomerA()));
            waysField.setText(String.valueOf(cu.ways()));
            rcdField.setText(cu.rcdDescription());
            for (int i = 0; i < cu.ways(); i++) {
                String cid = cu.circuitIdAtWay(i);
                String name = "- empty -";
                if (cid != null) {
                    name = project.findCircuit(cid).map(Circuit::name).orElse(cid);
                }
                wayRows.add(String.format(Locale.ROOT, "Way %d: %s", i + 1, name));
            }
            summaryLabel.setText(String.format(Locale.ROOT,
                    "%d circuit(s) · CU %s · %d ways · incomer %.0f A",
                    project.circuits().size(), cu.name(), cu.ways(), cu.incomerA()));
        } else {
            summaryLabel.setText(project.circuits().isEmpty()
                    ? "No circuits — recalculate or rebuild from plan."
                    : project.circuits().size() + " circuit(s) · no CU yet");
        }
        DesignReport report = project.lastReport();
        if (report != null) {
            for (ValidationIssue issue : report.issues()) {
                boolean rev = project.checklistReview().isReviewed(issue.code());
                checklistRows.add(new ChecklistRow(
                        issue.code(),
                        "[" + issue.severity() + "] " + issue.code() + " - " + issue.message(),
                        rev
                ));
            }
        }
    }

    private void rebuildCircuits() {
        if (project == null) {
            return;
        }
        CircuitMaterializer.rematerialize(project, loadCatalogue());
        modelChanged.run();
        refresh();
        statusSink.accept("Circuits rebuilt from plan geometry (" + project.circuits().size() + ")");
    }

    private static Map<String, ElectricalComponent> loadCatalogue() {
        Map<String, ElectricalComponent> map = new LinkedHashMap<>();
        try {
            ComponentLibraryService lib = LibraryBootstrap.get();
            if (lib == null) {
                return map;
            }
            for (ElectricalComponent c : lib.listAll()) {
                if (c != null && c.id() != null) {
                    map.put(c.id(), c);
                }
            }
        } catch (RuntimeException ignored) {
            // library optional in headless / early UI
        }
        return map;
    }

    private void applyCuSettings() {
        if (project == null) {
            return;
        }
        ConsumerUnit cu = project.ensureConsumerUnit();
        try {
            cu.setIncomerA(Double.parseDouble(incomerField.getText().trim()));
        } catch (Exception ignored) {
            // keep
        }
        try {
            cu.setWays(Integer.parseInt(waysField.getText().trim()));
        } catch (Exception ignored) {
            // keep
        }
        cu.setRcdDescription(rcdField.getText());
        cu.assignCircuitsInOrder(project.circuits());
        modelChanged.run();
        refresh();
        statusSink.accept("Consumer unit updated");
    }

    private record ChecklistRow(String code, String label, boolean reviewed) {
    }
}
