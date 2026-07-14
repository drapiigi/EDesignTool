package com.ghana.gwire.ui.panels;

import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.domain.project.ProjectSettings;
import com.ghana.gwire.ui.canvas.SelectionModel;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Project settings and selection properties.
 */
public class PropertiesPanel {

    private final VBox root;
    private final Label selectionTitle;
    private final Label selectionBody;
    private final TextField projectNameField;
    private final TextField houseTypeField;
    private final ComboBox<ProjectSettings.SupplyType> supplyCombo;
    private final TextField roomNameField;
    private final TextField heightField;
    private final ComboBox<CircuitChoice> circuitCombo;

    private Project project;
    private SelectionModel selection;
    private Runnable onProjectChanged = () -> {
    };
    private Runnable onGeometryChanged = () -> {
    };
    private boolean suppressSupplyEvents;
    private boolean suppressDeviceEvents;

    public PropertiesPanel() {
        Label title = new Label("Properties");
        title.getStyleClass().add("panel-title");

        Label projectHeading = new Label("Project");
        projectHeading.getStyleClass().add("panel-subtitle");

        projectNameField = new TextField();
        projectNameField.setPromptText("Project name");
        projectNameField.setOnAction(e -> applyProjectName());
        projectNameField.focusedProperty().addListener((o, was, is) -> {
            if (was && !is) {
                applyProjectName();
            }
        });

        houseTypeField = new TextField();
        houseTypeField.setPromptText("House type");
        houseTypeField.setOnAction(e -> applyHouseType());
        houseTypeField.focusedProperty().addListener((o, was, is) -> {
            if (was && !is) {
                applyHouseType();
            }
        });

        supplyCombo = new ComboBox<>();
        supplyCombo.getItems().addAll(ProjectSettings.SupplyType.values());
        supplyCombo.setMaxWidth(Double.MAX_VALUE);
        supplyCombo.setOnAction(e -> {
            if (suppressSupplyEvents) {
                return;
            }
            applySupply();
        });

        GridPane projectGrid = formGrid();
        int row = 0;
        projectGrid.add(label("Name"), 0, row);
        projectGrid.add(projectNameField, 1, row++);
        projectGrid.add(label("House"), 0, row);
        projectGrid.add(houseTypeField, 1, row++);
        projectGrid.add(label("Supply"), 0, row);
        projectGrid.add(supplyCombo, 1, row++);

        Label selHeading = new Label("Selection");
        selHeading.getStyleClass().add("panel-subtitle");
        selectionTitle = new Label("Nothing selected");
        selectionTitle.getStyleClass().add("panel-body");
        selectionBody = new Label(
                "Draw walls and rooms on the canvas.\n"
                        + "Import a PDF/image floor plan as background."
        );
        selectionBody.getStyleClass().add("panel-body");
        selectionBody.setWrapText(true);

        roomNameField = new TextField();
        roomNameField.setPromptText("Room name");
        roomNameField.setVisible(false);
        roomNameField.setManaged(false);
        roomNameField.setOnAction(e -> applyRoomName());
        roomNameField.focusedProperty().addListener((o, was, is) -> {
            if (was && !is) {
                applyRoomName();
            }
        });

        heightField = new TextField();
        heightField.setPromptText("Mounting height mm AFF");
        heightField.setVisible(false);
        heightField.setManaged(false);
        heightField.setOnAction(e -> applyMountingHeight());
        heightField.focusedProperty().addListener((o, was, is) -> {
            if (was && !is) {
                applyMountingHeight();
            }
        });

        circuitCombo = new ComboBox<>();
        circuitCombo.setMaxWidth(Double.MAX_VALUE);
        circuitCombo.setVisible(false);
        circuitCombo.setManaged(false);
        circuitCombo.setPromptText("Circuit");
        circuitCombo.setOnAction(e -> {
            if (!suppressDeviceEvents) {
                applyCircuitAssignment();
            }
        });

        Label standards = new Label(
                "Defaults (Ghana L.I. 2008 context):\n"
                        + "• 230 V / 50 Hz single-phase\n"
                        + "• Grid snap 500 mm\n"
                        + "• Units: millimetres on plan\n"
                        + "• Device heights: sockets 300 / switches 1200 / lights 2400 mm"
        );
        standards.getStyleClass().add("panel-footer");
        standards.setWrapText(true);

        VBox content = new VBox(10,
                title,
                projectHeading,
                projectGrid,
                selHeading,
                selectionTitle,
                roomNameField,
                heightField,
                circuitCombo,
                selectionBody,
                standards
        );
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("panel-scroll");

        root = new VBox(scroll);
        root.getStyleClass().addAll("side-panel", "properties-panel");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        setProject(null);
    }

    public VBox getRoot() {
        return root;
    }

    public void setOnProjectChanged(Runnable onProjectChanged) {
        this.onProjectChanged = onProjectChanged == null ? () -> {
        } : onProjectChanged;
    }

    public void setOnGeometryChanged(Runnable onGeometryChanged) {
        this.onGeometryChanged = onGeometryChanged == null ? () -> {
        } : onGeometryChanged;
    }

    public void setProject(Project project) {
        this.project = project;
        boolean enabled = project != null;
        projectNameField.setDisable(!enabled);
        houseTypeField.setDisable(!enabled);
        supplyCombo.setDisable(!enabled);
        if (project == null) {
            projectNameField.clear();
            houseTypeField.clear();
            supplyCombo.getSelectionModel().clearSelection();
            selectionTitle.setText("No project");
            selectionBody.setText("File → New Project to begin.");
            hideRoomName();
            return;
        }
        projectNameField.setText(project.name());
        houseTypeField.setText(project.settings().houseType());
        suppressSupplyEvents = true;
        try {
            supplyCombo.getSelectionModel().select(project.settings().supplyType());
        } finally {
            suppressSupplyEvents = false;
        }
    }

    public void showSelection(SelectionModel selection) {
        this.selection = selection;
        if (selection == null || selection.isEmpty()) {
            selectionTitle.setText("Nothing selected");
            selectionBody.setText("Select a wall, room, door, or window.");
            hideRoomName();
            hideDeviceFields();
            return;
        }
        switch (selection.kind()) {
            case WALL -> {
                Wall w = selection.wall();
                selectionTitle.setText("Wall");
                selectionBody.setText(
                        "Length: %.0f mm\nThickness: %.0f mm\nStart: (%.0f, %.0f)\nEnd: (%.0f, %.0f)"
                                .formatted(w.lengthMm(), w.thicknessMm(),
                                        w.start().x(), w.start().y(),
                                        w.end().x(), w.end().y())
                );
                hideRoomName();
                hideDeviceFields();
            }
            case ROOM -> {
                Room r = selection.room();
                selectionTitle.setText("Room");
                roomNameField.setText(r.name());
                roomNameField.setVisible(true);
                roomNameField.setManaged(true);
                selectionBody.setText(
                        "Size: %.0f × %.0f mm\nArea: %.2f m²\nOrigin: (%.0f, %.0f)"
                                .formatted(r.widthMm(), r.heightMm(), r.areaM2(), r.x(), r.y())
                );
                hideDeviceFields();
            }
            case OPENING -> {
                Opening o = selection.opening();
                selectionTitle.setText(o.type().name());
                selectionBody.setText(
                        "Width: %.0f mm\nPosition along wall: %.0f%%\nWall id: %s"
                                .formatted(o.widthMm(), o.t() * 100, shortId(o.wallId()))
                );
                hideRoomName();
                hideDeviceFields();
            }
            case DEVICE -> {
                var d = selection.device();
                selectionTitle.setText("Device");
                String circuitLabel = "—";
                if (d.circuitId() != null && project != null) {
                    circuitLabel = project.findCircuit(d.circuitId())
                            .map(Circuit::name)
                            .orElse(shortId(d.circuitId()));
                }
                selectionBody.setText(
                        "Name: %s\nComponent: %s\nSymbol: %s\nPosition: (%.0f, %.0f) mm\nRotation: %.0f°\nCircuit: %s"
                                .formatted(
                                        d.displayName(),
                                        d.componentId(),
                                        d.symbolKey(),
                                        d.xMm(),
                                        d.yMm(),
                                        d.rotationDeg(),
                                        circuitLabel
                                )
                );
                hideRoomName();
                showDeviceFields(d);
            }
            case NONE -> {
                selectionTitle.setText("Nothing selected");
                selectionBody.setText("Select a wall, room, door, or window.");
                hideRoomName();
                hideDeviceFields();
            }
        }
        if (project != null && project.floorPlan().background() != null) {
            BackgroundImage bg = project.floorPlan().background();
            selectionBody.setText(selectionBody.getText()
                    + "\n\nBackground: " + bg.sourceLabel()
                    + "\nScale: " + String.format("%.2f mm/px", bg.mmPerPixel()));
        }
    }

    private void hideRoomName() {
        roomNameField.setVisible(false);
        roomNameField.setManaged(false);
    }

    private void hideDeviceFields() {
        heightField.setVisible(false);
        heightField.setManaged(false);
        circuitCombo.setVisible(false);
        circuitCombo.setManaged(false);
    }

    private void showDeviceFields(com.ghana.gwire.domain.components.PlacedDevice d) {
        heightField.setVisible(true);
        heightField.setManaged(true);
        heightField.setText(d.mountingHeightMm() > 0
                ? String.format(java.util.Locale.ROOT, "%.0f", d.mountingHeightMm())
                : "");
        circuitCombo.setVisible(true);
        circuitCombo.setManaged(true);
        suppressDeviceEvents = true;
        try {
            circuitCombo.getItems().clear();
            circuitCombo.getItems().add(new CircuitChoice(null, "(unassigned)"));
            CircuitChoice selected = circuitCombo.getItems().get(0);
            if (project != null) {
                for (Circuit c : project.circuits()) {
                    CircuitChoice choice = new CircuitChoice(c.id(), c.name() + " [" + c.kind().name() + "]");
                    circuitCombo.getItems().add(choice);
                    if (c.id().equals(d.circuitId())) {
                        selected = choice;
                    }
                }
            }
            circuitCombo.getSelectionModel().select(selected);
        } finally {
            suppressDeviceEvents = false;
        }
    }

    private void applyProjectName() {
        if (project == null) {
            return;
        }
        project.setName(projectNameField.getText());
        onProjectChanged.run();
    }

    private void applyHouseType() {
        if (project == null) {
            return;
        }
        project.settings().setHouseType(houseTypeField.getText());
        project.touch();
        onProjectChanged.run();
    }

    private void applySupply() {
        if (project == null || supplyCombo.getValue() == null) {
            return;
        }
        project.settings().setSupplyType(supplyCombo.getValue());
        project.touch();
        onProjectChanged.run();
    }

    private void applyRoomName() {
        if (selection == null || selection.kind() != SelectionModel.Kind.ROOM) {
            return;
        }
        selection.room().setName(roomNameField.getText());
        if (project != null) {
            project.touch();
        }
        showSelection(selection);
        onGeometryChanged.run();
    }

    private void applyMountingHeight() {
        if (selection == null || selection.kind() != SelectionModel.Kind.DEVICE) {
            return;
        }
        try {
            String raw = heightField.getText() == null ? "" : heightField.getText().trim();
            double h = raw.isEmpty() ? 0 : Double.parseDouble(raw);
            selection.device().setMountingHeightMm(h);
            if (project != null) {
                project.touch();
            }
            onGeometryChanged.run();
        } catch (NumberFormatException ignored) {
            // keep previous
        }
    }

    private void applyCircuitAssignment() {
        if (selection == null || selection.kind() != SelectionModel.Kind.DEVICE || project == null) {
            return;
        }
        CircuitChoice choice = circuitCombo.getValue();
        if (choice == null) {
            return;
        }
        var device = selection.device();
        String oldId = device.circuitId();
        String newId = choice.id();
        if (Objects.equals(oldId, newId)) {
            return;
        }
        // Remove from previous circuit membership
        if (oldId != null) {
            project.findCircuit(oldId).ifPresent(c -> c.removeDeviceId(device.id()));
        }
        device.setCircuitId(newId);
        if (newId != null) {
            project.findCircuit(newId).ifPresent(c -> c.addDeviceId(device.id()));
        }
        project.touch();
        showSelection(selection);
        onGeometryChanged.run();
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.getColumnConstraints().clear();
        return grid;
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-body");
        return l;
    }

    private static String shortId(String id) {
        return id == null ? "" : id.substring(0, Math.min(8, id.length()));
    }

    private record CircuitChoice(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
