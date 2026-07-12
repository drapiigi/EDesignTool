package com.ghana.gwire.ui.menu;

import com.ghana.gwire.ui.MainWindow;
import com.ghana.gwire.ui.canvas.DrawTool;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Application menu bar wired to Phase 2 floor-plan actions.
 */
public class AppMenuBar {

    private final MenuBar menuBar;

    public AppMenuBar(MainWindow window) {
        Menu file = new Menu("_File");
        MenuItem newItem = item("_New Project", KeyCode.N, true, window::newProject);
        MenuItem openItem = item("_Open…", KeyCode.O, true, window::openProject);
        MenuItem saveItem = item("_Save", KeyCode.S, true, window::saveProject);
        MenuItem saveAsItem = item("Save _As…", null, false, window::saveProjectAs);
        MenuItem importItem = item("_Import Floor Plan…", KeyCode.I, true, window::importFloorPlan);
        MenuItem exportPdf = item("_Export PDF Report…", KeyCode.E, true, window::exportPdfReport);
        MenuItem exitItem = item("E_xit", KeyCode.Q, true, window::quit);
        file.getItems().addAll(
                newItem, openItem, saveItem, saveAsItem,
                new SeparatorMenuItem(),
                importItem,
                exportPdf,
                new SeparatorMenuItem(),
                exitItem
        );

        Menu edit = new Menu("_Edit");
        edit.getItems().addAll(
                item("_Undo", KeyCode.Z, true, window::undo),
                item("_Redo", KeyCode.Y, true, window::redo),
                new SeparatorMenuItem(),
                item("_Delete", KeyCode.DELETE, false, window::deleteSelection)
        );

        Menu view = new Menu("_View");
        CheckMenuItem darkMode = new CheckMenuItem("_Dark mode");
        darkMode.setSelected(window.getThemeManager().getTheme() == ThemeManager.Theme.DARK);
        darkMode.setOnAction(e -> {
            window.getThemeManager().setTheme(
                    darkMode.isSelected() ? ThemeManager.Theme.DARK : ThemeManager.Theme.LIGHT
            );
            window.getStatusBar().setMessage(
                    "Theme: " + window.getThemeManager().getTheme().label()
            );
        });
        view.getItems().addAll(
                darkMode,
                new SeparatorMenuItem(),
                item("Zoom _In", KeyCode.EQUALS, true, window::zoomIn),
                item("Zoom _Out", KeyCode.MINUS, true, window::zoomOut),
                item("_Fit to Window", KeyCode.DIGIT0, true, window::fitToWindow)
        );

        Menu design = new Menu("_Design");
        design.getItems().addAll(
                item("_AI Generate Design…", KeyCode.G, true, window::aiGenerateDesign),
                item("AI Generate (rules _only)…", null, false, window::aiGenerateDesignRulesOnly),
                item("Analyze Floor Plan (_Vision)…", null, false, window::analyzeFloorPlanVision),
                item("Vision + AI Design (_full)…", null, false, window::visionThenAiDesign),
                new SeparatorMenuItem(),
                item("Tool: _Select", null, false, () -> window.setTool(DrawTool.SELECT)),
                item("Tool: _Wall", null, false, () -> window.setTool(DrawTool.WALL)),
                item("Tool: _Room", null, false, () -> window.setTool(DrawTool.ROOM)),
                item("Tool: _Door", null, false, () -> window.setTool(DrawTool.DOOR)),
                item("Tool: Windo_w", null, false, () -> window.setTool(DrawTool.WINDOW)),
                item("Tool: _Pan", null, false, () -> window.setTool(DrawTool.PAN)),
                new SeparatorMenuItem(),
                item("Reload symbol _library", null, false, window::showComponentLibrary)
        );

        Menu tools = new Menu("_Tools");
        tools.getItems().addAll(
                item("_Recalculate Loads", KeyCode.R, true, window::recalculateLoads),
                item("_Validate Standards (L.I. 2008)", KeyCode.L, true, window::validateStandards),
                new SeparatorMenuItem(),
                item("Component _Library", null, false, window::showComponentLibrary),
                new SeparatorMenuItem(),
                item("AI _Co-pilot Chat…", null, false, window::aiCopilotChat)
        );

        Menu help = new Menu("_Help");
        MenuItem about = item("_About GhanaWire AI", null, false, window::showAbout);
        help.getItems().add(about);

        menuBar = new MenuBar(file, edit, view, design, tools, help);
        menuBar.setUseSystemMenuBar(false);
        menuBar.getStyleClass().add("app-menu-bar");
    }

    public MenuBar getMenuBar() {
        return menuBar;
    }

    private static MenuItem item(String text, KeyCode key, boolean control, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (key != null) {
            if (control) {
                mi.setAccelerator(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN));
            } else if (key == KeyCode.DELETE) {
                mi.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
            } else {
                mi.setAccelerator(new KeyCodeCombination(key));
            }
        }
        mi.setOnAction(e -> action.run());
        return mi;
    }

    private static MenuItem disabled(String text) {
        MenuItem mi = new MenuItem(text);
        mi.setDisable(true);
        return mi;
    }
}
