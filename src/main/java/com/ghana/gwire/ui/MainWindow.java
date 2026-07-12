package com.ghana.gwire.ui;

import com.ghana.gwire.GWireApp;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.ui.canvas.DrawTool;
import com.ghana.gwire.ui.canvas.FloorPlanWorkspace;
import com.ghana.gwire.ui.menu.AppMenuBar;
import com.ghana.gwire.ui.panels.BoqPanel;
import com.ghana.gwire.ui.panels.PropertiesPanel;
import com.ghana.gwire.ui.panels.StatusBar;
import com.ghana.gwire.ui.panels.SymbolLibraryPanel;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Primary application chrome: library, floor-plan workspace, properties, BOQ, status.
 */
public class MainWindow {

    private final Stage stage;
    private final ThemeManager themeManager;
    private final BorderPane root;
    private final StatusBar statusBar;
    private final FloorPlanWorkspace workspace;
    private final PropertiesPanel propertiesPanel;
    private final SymbolLibraryPanel symbolLibraryPanel;
    private final BoqPanel boqPanel;
    private final AppMenuBar menuBar;

    private Project project;

    public MainWindow(Stage stage, ThemeManager themeManager) {
        this.stage = stage;
        this.themeManager = themeManager;
        this.statusBar = new StatusBar();
        this.workspace = new FloorPlanWorkspace();
        this.propertiesPanel = new PropertiesPanel();
        this.symbolLibraryPanel = new SymbolLibraryPanel();
        this.boqPanel = new BoqPanel();
        this.menuBar = new AppMenuBar(this);

        workspace.setOwnerWindow(stage);
        workspace.setStatusSink(statusBar::setMessage);
        workspace.setSelectionListener(this::refreshSelection);

        propertiesPanel.setOnProjectChanged(this::refreshTitleAndStatus);
        propertiesPanel.setOnGeometryChanged(() -> {
            workspace.getCanvas().redraw();
            refreshSelection();
            boqPanel.refresh();
        });

        symbolLibraryPanel.setStatusSink(statusBar::setMessage);

        SplitPane rightSplit = new SplitPane(propertiesPanel.getRoot(), boqPanel.getRoot());
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.55);
        rightSplit.setPrefWidth(300);

        SplitPane centerSplit = new SplitPane(
                symbolLibraryPanel.getRoot(),
                workspace.getRoot(),
                rightSplit
        );
        centerSplit.setOrientation(Orientation.HORIZONTAL);
        centerSplit.setDividerPositions(0.22, 0.72);
        VBox.setVgrow(centerSplit, Priority.ALWAYS);

        VBox centerColumn = new VBox(centerSplit);
        centerColumn.getStyleClass().add("workspace");

        root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(menuBar.getMenuBar());
        root.setCenter(centerColumn);
        root.setBottom(statusBar.getRoot());

        createProject("Untitled project", false);
        int count = 0;
        try {
            if (LibraryBootstrap.get() != null) {
                count = LibraryBootstrap.get().count();
            }
        } catch (Exception ignored) {
            // library optional at UI build time
        }
        statusBar.setMessage(
                "Drag components from the library onto the plan · drag placed symbols to move ("
                        + count + " catalogue items)."
        );
        statusBar.setSecondary("Standards: Ghana L.I. 2008 · 230 V / 50 Hz");
    }

    public BorderPane getRoot() {
        return root;
    }

    public Stage getStage() {
        return stage;
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public FloorPlanWorkspace getWorkspace() {
        return workspace;
    }

    public Project getProject() {
        return project;
    }

    public void newProject() {
        TextInputDialog dialog = new TextInputDialog("New residence");
        dialog.initOwner(stage);
        dialog.setTitle("New project");
        dialog.setHeaderText("Create a floor plan project");
        dialog.setContentText("Project name:");
        dialog.showAndWait().ifPresent(name -> createProject(name, true));
    }

    private void createProject(String name, boolean announce) {
        project = new Project(name);
        workspace.bindProject(project);
        propertiesPanel.setProject(project);
        boqPanel.setProject(project);
        refreshTitleAndStatus();
        refreshSelection();
        if (announce) {
            statusBar.setMessage("New project: " + project.name());
        }
    }

    public void openProject() {
        statusBar.setMessage("Open project file — full project I/O arrives with persistence (Phase 6).");
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.initOwner(stage);
        info.setTitle("Open project");
        info.setHeaderText("Not yet available");
        info.setContentText(
                "Phase 3 supports floor-plan editing, import, and the component library.\n"
                        + "Saving/loading .gwire project files is planned with the persistence layer."
        );
        info.showAndWait();
    }

    public void saveProject() {
        statusBar.setMessage("Save project — file format arrives with persistence (Phase 6).");
    }

    public void importFloorPlan() {
        workspace.importFloorPlan();
    }

    public void showComponentLibrary() {
        symbolLibraryPanel.reload();
        statusBar.setMessage("Component library reloaded ("
                + (LibraryBootstrap.get() == null ? 0 : LibraryBootstrap.get().count())
                + " items)");
    }

    public void setTool(DrawTool tool) {
        workspace.setTool(tool);
    }

    public void undo() {
        workspace.getCanvas().undo();
        boqPanel.refresh();
        refreshTitleAndStatus();
    }

    public void redo() {
        workspace.getCanvas().redo();
        boqPanel.refresh();
        refreshTitleAndStatus();
    }

    public void deleteSelection() {
        workspace.getCanvas().deleteSelection();
        boqPanel.refresh();
        refreshTitleAndStatus();
    }

    public void zoomIn() {
        workspace.getCanvas().zoomIn();
    }

    public void zoomOut() {
        workspace.getCanvas().zoomOut();
    }

    public void fitToWindow() {
        workspace.getCanvas().fitToWindow();
    }

    public void showAbout() {
        int n = LibraryBootstrap.get() == null ? 0 : LibraryBootstrap.get().count();
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.initOwner(stage);
        about.setTitle("About " + GWireApp.APP_NAME);
        about.setHeaderText(GWireApp.APP_NAME + " (" + GWireApp.APP_SHORT_NAME + ")");
        about.setContentText(
                """
                Version: %s

                AI-assisted electrical wiring design for Ghana.
                Targets Ghana Electrical Wiring Regulations 2011 (L.I. 2008),
                Energy Commission guidelines, and Ghana Standards.

                Stack: Java 21+, JavaFX 23+, Maven, H2, PDFBox

                Phase 3: Symbol library + starter component DB (%d items).
                """.formatted(GWireApp.APP_VERSION, n)
        );
        about.showAndWait();
    }

    public void quit() {
        stage.close();
    }

    private void refreshSelection() {
        propertiesPanel.showSelection(workspace.getSelection());
        boqPanel.refresh();
        refreshTitleAndStatus();
    }

    private void refreshTitleAndStatus() {
        if (project == null) {
            stage.setTitle(GWireApp.APP_NAME + " — " + GWireApp.APP_SHORT_NAME);
            statusBar.setSecondary("No project");
            return;
        }
        stage.setTitle(GWireApp.APP_NAME + " — " + project.name());
        statusBar.setSecondary(
                "L.I. 2008 · " + project.supplySummary()
                        + " · walls " + project.floorPlan().walls().size()
                        + " · rooms " + project.floorPlan().rooms().size()
                        + " · devices " + project.floorPlan().devices().size()
        );
    }
}
