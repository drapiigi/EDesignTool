package com.ghana.gwire;

import com.ghana.gwire.ui.MainWindow;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GhanaWire AI (G-Wire Designer) — primary JavaFX application.
 *
 * <p>Phase 1: application shell, menus, theming, and layout placeholders
 * for floor plan canvas, properties, and BOQ panels.
 */
public class GWireApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(GWireApp.class);

    public static final String APP_NAME = "GhanaWire AI";
    public static final String APP_SHORT_NAME = "G-Wire Designer";
    public static final String APP_VERSION = "0.1.0-SNAPSHOT";

    @Override
    public void start(Stage stage) {
        log.info("Starting {} {} ({})", APP_NAME, APP_SHORT_NAME, APP_VERSION);

        ThemeManager themeManager = new ThemeManager();
        MainWindow mainWindow = new MainWindow(stage, themeManager);

        Scene scene = new Scene(mainWindow.getRoot(), 1280, 800);
        themeManager.apply(scene);

        stage.setTitle(APP_NAME + " — " + APP_SHORT_NAME);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        log.info("UI ready");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
