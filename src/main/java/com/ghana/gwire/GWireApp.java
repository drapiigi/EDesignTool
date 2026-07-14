package com.ghana.gwire;

import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.ui.ErrorDialog;
import com.ghana.gwire.ui.MainWindow;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GhanaWire AI (G-Wire Designer) — primary JavaFX application.
 */
public class GWireApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(GWireApp.class);

    public static final String APP_NAME = "GhanaWire AI";
    public static final String APP_SHORT_NAME = "G-Wire Designer";
    public static final String APP_VERSION = "0.5.0-SNAPSHOT";

    private MainWindow mainWindow;

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Uncaught exception on thread {}", t.getName(), e);
            ErrorDialog.show(stage, "Unexpected error", e);
        });
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
            log.error("Uncaught exception on FX thread", e);
            ErrorDialog.show(stage, "Unexpected error", e);
        });

        log.info("Starting {} {} ({})", APP_NAME, APP_SHORT_NAME, APP_VERSION);

        try {
            LibraryBootstrap.initialize();
        } catch (Exception e) {
            log.error("Component library init failed (UI will continue): {}", e.getMessage(), e);
        }

        ThemeManager themeManager = new ThemeManager();
        mainWindow = new MainWindow(stage, themeManager);

        Scene scene = new Scene(mainWindow.getRoot(), 1280, 800);
        themeManager.apply(scene);

        stage.setTitle(APP_NAME + " — " + APP_SHORT_NAME);
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        Platform.runLater(() -> mainWindow.checkCrashRecovery());

        log.info("UI ready");
    }

    @Override
    public void stop() {
        if (mainWindow != null) {
            mainWindow.performCleanExit();
        }
        LibraryBootstrap.shutdown();
        log.info("Shutdown complete");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
