package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/**
 * Multi-storey switcher and add-floor controls (Phase 9).
 * Combo updates are suppressed during programmatic refresh to avoid recursive onAction → StackOverflowError.
 */
public class StoreyBar {

    private final HBox root;
    private final ComboBox<BuildingStorey> storeyCombo = new ComboBox<>();
    private final Label info = new Label();
    private Project project;
    private Consumer<Integer> onStoreyChanged = i -> {
    };
    private Runnable onStructureChanged = () -> {
    };
    /** When true, combo selection changes do not notify listeners. */
    private boolean suppressEvents;

    public StoreyBar() {
        root = new HBox(8);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(4, 10, 4, 10));
        root.getStyleClass().add("drawing-toolbar");

        Label lbl = new Label("Storey:");
        lbl.getStyleClass().add("panel-body");
        storeyCombo.setPrefWidth(200);
        storeyCombo.setOnAction(e -> {
            if (suppressEvents || project == null || storeyCombo.getValue() == null) {
                return;
            }
            int idx = project.storeys().indexOf(storeyCombo.getValue());
            if (idx < 0) {
                return;
            }
            // Avoid re-entry when selection already matches active storey
            if (idx == project.activeStoreyIndex()) {
                return;
            }
            onStoreyChanged.accept(idx);
        });

        Button add = new Button("+ Floor");
        add.getStyleClass().add("tool-action");
        add.setTooltip(new Tooltip("Add upper floor storey"));
        add.setOnAction(e -> addFloor());

        Button remove = new Button("− Floor");
        remove.getStyleClass().add("tool-action");
        remove.setTooltip(new Tooltip("Remove active storey (min one floor)"));
        remove.setOnAction(e -> removeFloor());

        info.getStyleClass().add("panel-footer");

        root.getChildren().addAll(lbl, storeyCombo, add, remove, info);
    }

    public HBox getRoot() {
        return root;
    }

    public void setOnStoreyChanged(Consumer<Integer> onStoreyChanged) {
        this.onStoreyChanged = onStoreyChanged == null ? i -> {
        } : onStoreyChanged;
    }

    public void setOnStructureChanged(Runnable onStructureChanged) {
        this.onStructureChanged = onStructureChanged == null ? () -> {
        } : onStructureChanged;
    }

    public void bindProject(Project project) {
        this.project = project;
        refresh();
    }

    public void refresh() {
        suppressEvents = true;
        try {
            if (project == null) {
                storeyCombo.setItems(FXCollections.observableArrayList());
                info.setText("");
                return;
            }
            storeyCombo.setItems(FXCollections.observableArrayList(project.storeys()));
            if (!project.storeys().isEmpty()) {
                int idx = Math.clamp(project.activeStoreyIndex(), 0, project.storeys().size() - 1);
                storeyCombo.getSelectionModel().select(idx);
            }
            info.setText(project.storeys().size() + " level(s)");
        } finally {
            suppressEvents = false;
        }
    }

    private void addFloor() {
        if (project == null) {
            return;
        }
        int maxLevel = project.storeys().stream().mapToInt(BuildingStorey::level).max().orElse(0);
        project.addStorey("Floor " + (maxLevel + 1), maxLevel + 1);
        int newIndex = project.storeys().size() - 1;
        project.setActiveStoreyIndex(newIndex);
        refresh();
        onStoreyChanged.accept(newIndex);
        onStructureChanged.run();
    }

    private void removeFloor() {
        if (project == null || project.storeys().size() <= 1) {
            return;
        }
        int idx = project.activeStoreyIndex();
        if (project.removeStoreyAt(idx)) {
            refresh();
            onStoreyChanged.accept(project.activeStoreyIndex());
            onStructureChanged.run();
        }
    }
}
