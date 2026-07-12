package com.ghana.gwire.ui;

import com.ghana.gwire.GWireApp;
import com.ghana.gwire.ui.menu.AppMenuBar;
import com.ghana.gwire.ui.panels.BoqPanel;
import com.ghana.gwire.ui.panels.CanvasPlaceholder;
import com.ghana.gwire.ui.panels.PropertiesPanel;
import com.ghana.gwire.ui.panels.StatusBar;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Primary application chrome: menu, toolbar area, multi-pane workspace, status bar.
 */
public class MainWindow {

    private final Stage stage;
    private final ThemeManager themeManager;
    private final BorderPane root;
    private final StatusBar statusBar;
    private final CanvasPlaceholder canvasPlaceholder;

    public MainWindow(Stage stage, ThemeManager themeManager) {
        this.stage = stage;
        this.themeManager = themeManager;
        this.statusBar = new StatusBar();
        this.canvasPlaceholder = new CanvasPlaceholder();

        AppMenuBar menuBar = new AppMenuBar(this);

        PropertiesPanel propertiesPanel = new PropertiesPanel();
        BoqPanel boqPanel = new BoqPanel();

        SplitPane rightSplit = new SplitPane(propertiesPanel.getRoot(), boqPanel.getRoot());
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.55);
        rightSplit.setPrefWidth(320);

        SplitPane centerSplit = new SplitPane(canvasPlaceholder.getRoot(), rightSplit);
        centerSplit.setOrientation(Orientation.HORIZONTAL);
        centerSplit.setDividerPositions(0.72);
        VBox.setVgrow(centerSplit, Priority.ALWAYS);

        VBox centerColumn = new VBox(centerSplit);
        centerColumn.getStyleClass().add("workspace");

        root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(menuBar.getMenuBar());
        root.setCenter(centerColumn);
        root.setBottom(statusBar.getRoot());

        statusBar.setMessage("Ready — Phase 1 shell. Open a project or start a new floor plan.");
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

    public void newProject() {
        statusBar.setMessage("New project — floor plan module arrives in Phase 2.");
        canvasPlaceholder.setHeadline("New project");
        canvasPlaceholder.setDetail("Floor plan designer will open here in Phase 2.");
    }

    public void openProject() {
        statusBar.setMessage("Open project — project I/O arrives in a later phase.");
    }

    public void saveProject() {
        statusBar.setMessage("Save project — not yet implemented (Phase 6+).");
    }

    public void showAbout() {
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

                Phase 1: Application shell and menus.
                """.formatted(GWireApp.APP_VERSION)
        );
        about.showAndWait();
    }

    public void quit() {
        stage.close();
    }
}
