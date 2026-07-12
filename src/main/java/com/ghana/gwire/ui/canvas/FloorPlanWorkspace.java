package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.history.FloorPlanHistory;
import com.ghana.gwire.service.importing.FloorPlanImportService;
import com.ghana.gwire.service.importing.ImportedRaster;
import javafx.scene.control.Alert;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.function.Consumer;

/**
 * Toolbar + canvas workspace for Phase 2 floor planning.
 */
public class FloorPlanWorkspace {

    private static final Logger log = LoggerFactory.getLogger(FloorPlanWorkspace.class);

    private final VBox root;
    private final DrawingToolbar toolbar;
    private final StoreyBar storeyBar;
    private final FloorPlanCanvas canvas;
    private final FloorPlanHistory history;
    private final FloorPlanImportService importService = new FloorPlanImportService();

    private Project project;
    private Consumer<String> statusSink = s -> {
    };
    private Runnable selectionListener = () -> {
    };
    private Runnable modelChangeListener = () -> {
    };
    private Runnable storeyChangeListener = () -> {
    };
    private Window ownerWindow;

    public FloorPlanWorkspace() {
        history = new FloorPlanHistory(evt -> {
            // no-op; canvas listens via undo/redo methods
        });
        canvas = new FloorPlanCanvas(history);
        toolbar = new DrawingToolbar();
        storeyBar = new StoreyBar();

        toolbar.setToolListener(canvas::setTool);
        canvas.toolProperty().addListener((o, a, b) -> toolbar.selectTool(b));
        toolbar.setImportAction(this::importFloorPlan);
        toolbar.setClearBgAction(this::clearBackground);
        toolbar.setFitAction(canvas::fitToWindow);

        storeyBar.setOnStoreyChanged(this::switchStorey);
        storeyBar.setOnStructureChanged(() -> {
            modelChangeListener.run();
            storeyChangeListener.run();
        });

        canvas.setStatusSink(msg -> statusSink.accept(msg));
        canvas.setSelectionListener(() -> selectionListener.run());
        canvas.setModelChangeListener(() -> modelChangeListener.run());

        VBox.setVgrow(canvas.getRoot(), Priority.ALWAYS);
        root = new VBox(toolbar.getRoot(), storeyBar.getRoot(), canvas.getRoot());
        root.getStyleClass().add("floor-plan-workspace");
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    public VBox getRoot() {
        return root;
    }

    public FloorPlanCanvas getCanvas() {
        return canvas;
    }

    public FloorPlanHistory getHistory() {
        return history;
    }

    public SelectionModel getSelection() {
        return canvas.getSelection();
    }

    public Project getProject() {
        return project;
    }

    public void setOwnerWindow(Window ownerWindow) {
        this.ownerWindow = ownerWindow;
    }

    public void setStatusSink(Consumer<String> statusSink) {
        this.statusSink = statusSink == null ? s -> {
        } : statusSink;
        canvas.setStatusSink(this.statusSink);
    }

    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener == null ? () -> {
        } : selectionListener;
        // Selection-only updates must NOT call modelChangeListener (that marks dirty / clears calc
        // and previously re-entered via BOQ/status refresh chains). Device moves call
        // modelChangeListener from canvas drag-end via status callbacks where needed.
        canvas.setSelectionListener(() -> this.selectionListener.run());
    }

    public void setModelChangeListener(Runnable modelChangeListener) {
        this.modelChangeListener = modelChangeListener == null ? () -> {
        } : modelChangeListener;
    }

    public void setStoreyChangeListener(Runnable storeyChangeListener) {
        this.storeyChangeListener = storeyChangeListener == null ? () -> {
        } : storeyChangeListener;
    }

    public void bindProject(Project project) {
        this.project = project;
        storeyBar.bindProject(project);
        if (project == null) {
            canvas.setFloorPlan(new FloorPlan());
            canvas.clearRasterCache();
        } else {
            canvas.setFloorPlan(project.floorPlan());
            reloadBackgroundRaster();
        }
        statusSink.accept("Project: " + (project == null ? "(none)" : project.name()));
    }

    private void switchStorey(int index) {
        if (project == null) {
            return;
        }
        int clamped = Math.clamp(index, 0, Math.max(0, project.storeys().size() - 1));
        // Idempotent: avoid refresh → combo select → onAction → switchStorey loops
        if (clamped == project.activeStoreyIndex()
                && canvas.getFloorPlan() == project.floorPlan()) {
            return;
        }
        project.setActiveStoreyIndex(clamped);
        canvas.setFloorPlan(project.floorPlan());
        reloadBackgroundRaster();
        canvas.fitToWindow();
        storeyBar.refresh();
        storeyChangeListener.run();
        statusSink.accept("Active storey: " + project.activeStorey().displayLabel());
    }

    public void refreshStoreyBar() {
        storeyBar.refresh();
    }

    /**
     * Re-loads background image bytes into the canvas cache from the project path.
     */
    public void reloadBackgroundRaster() {
        if (project == null || project.floorPlan().background() == null) {
            return;
        }
        BackgroundImage bg = project.floorPlan().background();
        try {
            java.nio.file.Path path = java.nio.file.Path.of(bg.sourcePath());
            if (!java.nio.file.Files.isRegularFile(path)) {
                statusSink.accept("Background image missing on disk: " + bg.sourceLabel());
                return;
            }
            ImportedRaster raster = importService.importFile(path);
            canvas.registerRaster(raster.sourcePath(), raster.image());
            canvas.redraw();
        } catch (Exception ex) {
            log.warn("Could not reload background raster: {}", ex.getMessage());
            statusSink.accept("Could not load background: " + ex.getMessage());
        }
    }

    public void setTool(DrawTool tool) {
        canvas.setTool(tool);
        toolbar.selectTool(tool);
    }

    public void importFloorPlan() {
        if (project == null) {
            alert("No project", "Create or open a project first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import floor plan (image or PDF)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Floor plans", "*.png", "*.jpg", "*.jpeg",
                        "*.gif", "*.bmp", "*.webp", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg",
                        "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File file = chooser.showOpenDialog(ownerWindow);
        if (file == null) {
            return;
        }
        try {
            ImportedRaster raster = importService.importFile(file.toPath());
            history.push(project.floorPlan());
            BackgroundImage bg = new BackgroundImage(
                    raster.sourcePath(),
                    raster.label(),
                    raster.suggestedMmPerPixel()
            );
            project.floorPlan().setBackground(bg);
            project.touch();
            canvas.registerRaster(raster.sourcePath(), raster.image());
            canvas.fitToWindow();
            canvas.redraw();
            modelChangeListener.run();
            statusSink.accept("Imported " + raster.label()
                    + " · scale ~" + String.format("%.1f", raster.suggestedMmPerPixel()) + " mm/px");
        } catch (Exception ex) {
            log.error("Import failed", ex);
            alert("Import failed", ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    public void clearBackground() {
        if (project == null || project.floorPlan().background() == null) {
            statusSink.accept("No background to clear");
            return;
        }
        history.push(project.floorPlan());
        project.floorPlan().clearBackground();
        project.touch();
        canvas.redraw();
        modelChangeListener.run();
        statusSink.accept("Background cleared");
    }

    private void alert(String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        if (ownerWindow != null) {
            a.initOwner(ownerWindow);
        }
        a.setTitle("GhanaWire AI");
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }
}
