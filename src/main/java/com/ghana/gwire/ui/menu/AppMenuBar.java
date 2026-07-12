package com.ghana.gwire.ui.menu;

import com.ghana.gwire.ui.MainWindow;
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
 * Application menu bar. Phase 1 wires shell actions; later phases fill handlers.
 */
public class AppMenuBar {

    private final MenuBar menuBar;

    public AppMenuBar(MainWindow window) {
        Menu file = new Menu("_File");
        MenuItem newItem = item("_New Project", KeyCode.N, true, window::newProject);
        MenuItem openItem = item("_Open…", KeyCode.O, true, window::openProject);
        MenuItem saveItem = item("_Save", KeyCode.S, true, window::saveProject);
        MenuItem exitItem = item("E_xit", KeyCode.Q, true, window::quit);
        file.getItems().addAll(newItem, openItem, saveItem, new SeparatorMenuItem(), exitItem);

        Menu edit = new Menu("_Edit");
        edit.getItems().addAll(
                disabled("_Undo"),
                disabled("_Redo"),
                new SeparatorMenuItem(),
                disabled("Cu_t"),
                disabled("_Copy"),
                disabled("_Paste"),
                disabled("_Delete")
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
                disabled("Zoom _In"),
                disabled("Zoom _Out"),
                disabled("_Fit to Window")
        );

        Menu design = new Menu("_Design");
        design.getItems().addAll(
                disabled("_AI Generate Design…"),
                disabled("Add _Room"),
                disabled("Add _Wall"),
                new SeparatorMenuItem(),
                disabled("Insert _Symbol…")
        );

        Menu tools = new Menu("_Tools");
        tools.getItems().addAll(
                disabled("_Validate Standards (L.I. 2008)"),
                disabled("_Recalculate Loads"),
                disabled("Component _Library…"),
                new SeparatorMenuItem(),
                disabled("AI _Co-pilot Chat…")
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
            mi.setAccelerator(new KeyCodeCombination(
                    key,
                    control ? KeyCombination.SHORTCUT_DOWN : KeyCombination.SHORTCUT_ANY
            ));
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
