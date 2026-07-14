package com.ghana.gwire.ui.dialogs;

import com.ghana.gwire.GWireApp;
import com.ghana.gwire.service.prefs.UserPrefs;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

/**
 * First-run liability / CEWP acknowledgment for beta builds.
 *
 * @return true if the user accepted and may continue; false if they declined (app should exit)
 */
public final class FirstRunDialog {

    private FirstRunDialog() {
    }

    /**
     * Shows the dialog if not yet accepted. Returns false if the user refuses.
     */
    public static boolean ensureAccepted(Stage owner, UserPrefs prefs) {
        if (prefs.isFirstRunAccepted()) {
            return true;
        }
        return show(owner, prefs);
    }

    public static boolean show(Window owner, UserPrefs prefs) {
        boolean unsigned = !prefs.isBuildSigned();

        String body = """
                GhanaWire AI is a preliminary design aid for household electrical wiring
                in the context of Ghana Electrical Wiring Regulations 2011 (L.I. 2008)
                and related Energy Commission / Ghana Standards practice.

                IMPORTANT — PLEASE READ

                • Outputs (loads, cable sizes, validation messages, BOQ, PDF) are
                  simplified engineering heuristics for education and early design.
                • They are NOT a substitute for a full design by a Competent Electrical
                  Wiring Professional (CEWP) or licensed electrician.
                • Always verify calculations, protective devices, and installation methods
                  on site before purchase or installation.
                • AI features (if enabled) can err; treat suggestions as drafts only.

                By continuing you acknowledge that you will not rely solely on this
                software for statutory certification or live electrical work.
                """;

        if (unsigned) {
            body += """

                    UNSIGNED BETA BUILD
                    This package is not code-signed. Download only from a trusted source
                    (e.g. the official project releases) and verify checksums when published.
                    """;
        }

        TextArea area = new TextArea(body);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(16);
        area.setPrefColumnCount(52);
        area.setFocusTraversable(false);

        CheckBox cewp = new CheckBox(
                "I understand this is a design aid only and a CEWP must verify real installations."
        );
        cewp.setWrapText(true);

        Label version = new Label(GWireApp.APP_NAME + "  ·  " + GWireApp.APP_VERSION
                + "  ·  " + GWireApp.standardsStamp());
        version.setWrapText(true);
        version.getStyleClass().add("panel-footer");

        VBox box = new VBox(10, version, area, cewp);
        box.setPadding(new Insets(4));

        ButtonType accept = new ButtonType("Accept and continue", ButtonBar.ButtonData.OK_DONE);
        ButtonType decline = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Welcome — " + GWireApp.APP_NAME);
        alert.setHeaderText("Beta disclaimer & CEWP acknowledgment");
        alert.getDialogPane().setContent(box);
        alert.getButtonTypes().setAll(accept, decline);

        // Disable Accept until checkbox is ticked
        var acceptBtn = alert.getDialogPane().lookupButton(accept);
        acceptBtn.setDisable(true);
        cewp.selectedProperty().addListener((o, a, b) -> acceptBtn.setDisable(!b));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == accept && cewp.isSelected()) {
            prefs.setFirstRunAccepted(GWireApp.APP_VERSION);
            return true;
        }
        return false;
    }
}
