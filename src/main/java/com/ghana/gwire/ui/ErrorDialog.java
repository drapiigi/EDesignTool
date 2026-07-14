package com.ghana.gwire.ui;

import com.ghana.gwire.GWireApp;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User-facing error dialog for uncaught exceptions. Never dumps full project JSON.
 */
public final class ErrorDialog {

    private static final Logger log = LoggerFactory.getLogger(ErrorDialog.class);

    private ErrorDialog() {
    }

    public static void show(String title, Throwable error) {
        show(null, title, error);
    }

    public static void show(Stage owner, String title, Throwable error) {
        String raw = error == null ? "Unknown error" : error.getMessage();
        if (raw == null || raw.isBlank()) {
            raw = error == null ? "Unknown error" : error.getClass().getSimpleName();
        }
        // Truncate; never include huge payloads
        if (raw.length() > 500) {
            raw = raw.substring(0, 500) + "…";
        }
        final String msg = raw;
        final String header = title == null ? "Unexpected error" : title;
        log.error("{}: {}", header, msg, error);

        Runnable show = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setTitle(GWireApp.APP_NAME);
            alert.setHeaderText(header);
            alert.setContentText(msg);

            if (error != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(error.getClass().getName()).append(": ").append(error.getMessage()).append('\n');
                StackTraceElement[] st = error.getStackTrace();
                int limit = Math.min(st.length, 12);
                for (int i = 0; i < limit; i++) {
                    sb.append("  at ").append(st[i]).append('\n');
                }
                TextArea area = new TextArea(sb.toString());
                area.setEditable(false);
                area.setWrapText(true);
                area.setMaxWidth(Double.MAX_VALUE);
                area.setMaxHeight(Double.MAX_VALUE);
                GridPane.setVgrow(area, Priority.ALWAYS);
                GridPane.setHgrow(area, Priority.ALWAYS);
                GridPane exp = new GridPane();
                exp.setMaxWidth(Double.MAX_VALUE);
                exp.add(area, 0, 0);
                alert.getDialogPane().setExpandableContent(exp);
            }
            alert.showAndWait();
        };

        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }
}
