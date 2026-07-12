package com.ghana.gwire.ui.panels;

import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.calc.ValidationIssue;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Shows Phase 4 calculation summary: totals, circuit schedule, validation issues.
 */
public class CalcResultsPanel {

    private final VBox root;
    private final Label summaryLabel;
    private final TableView<CircuitLoad> circuitTable;
    private final ObservableList<CircuitLoad> circuitRows = FXCollections.observableArrayList();
    private final ListView<ValidationIssue> issuesList;
    private final ObservableList<ValidationIssue> issueRows = FXCollections.observableArrayList();

    public CalcResultsPanel() {
        Label title = new Label("Calculation & standards");
        title.getStyleClass().add("panel-title");

        summaryLabel = new Label("Run Tools → Recalculate Loads to generate a design report.");
        summaryLabel.getStyleClass().add("panel-body");
        summaryLabel.setWrapText(true);

        circuitTable = new TableView<>(circuitRows);
        circuitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        circuitTable.setPlaceholder(new Label("No circuits calculated yet."));
        circuitTable.setPrefHeight(140);
        circuitTable.getColumns().add(col("Circuit", c -> c.name()));
        circuitTable.getColumns().add(col("Kind", c -> c.kind().name()));
        circuitTable.getColumns().add(col("P (W)", c -> String.format("%.0f", c.connectedLoadW())));
        circuitTable.getColumns().add(col("I (A)", c -> String.format("%.1f", c.designCurrentA())));
        circuitTable.getColumns().add(col("Cable", c -> nullToDash(c.recommendedCableSize())));
        circuitTable.getColumns().add(col("MCB", c -> c.recommendedBreakerA() > 0
                ? String.format("%.0fA", c.recommendedBreakerA()) : "—"));
        circuitTable.getColumns().add(col("Vd %", c -> String.format("%.2f", c.voltageDropPercent())));
        circuitTable.getColumns().add(col("L (m)", c -> String.format("%.1f", c.estimatedLengthM())));

        Label issuesTitle = new Label("Validation (L.I. 2008 practice — illustrative)");
        issuesTitle.getStyleClass().add("panel-subtitle");

        issuesList = new ListView<>(issueRows);
        issuesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ValidationIssue item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("issue-error", "issue-warning", "issue-info");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText("[" + item.severity() + "] " + item.code() + " — " + item.message());
                if (item.severity() == Severity.ERROR) {
                    getStyleClass().add("issue-error");
                } else if (item.severity() == Severity.WARNING) {
                    getStyleClass().add("issue-warning");
                } else {
                    getStyleClass().add("issue-info");
                }
            }
        });
        issuesList.setPlaceholder(new Label("No issues — run calculation first."));
        VBox.setVgrow(issuesList, Priority.ALWAYS);

        Label disclaimer = new Label(
                "Heuristics for preliminary design only. Have a CEWP verify before installation."
        );
        disclaimer.getStyleClass().add("panel-footer");
        disclaimer.setWrapText(true);

        VBox content = new VBox(8, title, summaryLabel, circuitTable, issuesTitle, issuesList, disclaimer);
        content.setPadding(new Insets(12));
        content.getStyleClass().add("panel-content");
        VBox.setVgrow(issuesList, Priority.ALWAYS);

        root = new VBox(content);
        root.getStyleClass().addAll("side-panel", "calc-results-panel");
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    public VBox getRoot() {
        return root;
    }

    public void showReport(DesignReport report) {
        circuitRows.clear();
        issueRows.clear();
        if (report == null) {
            summaryLabel.setText("No calculation report yet.");
            return;
        }
        summaryLabel.setText(String.format(
                """
                Project: %s
                Supply: %s
                Connected: %.0f W · After diversity: %.0f W (×%.2f) · Design I: %.1f A
                Circuits: %d · Max Vd: %.2f%% · Issues: %d error(s), %d warning(s)
                Calculated: %s
                """,
                report.projectName(),
                report.supplyTypeSummary(),
                report.totalConnectedLoadW(),
                report.totalAfterDiversityW(),
                report.diversityApplied(),
                report.totalDesignCurrentA(),
                report.circuits().size(),
                report.maxVoltageDropPercent(),
                report.errorCount(),
                report.warningCount(),
                report.calculatedAt()
        ).trim());
        circuitRows.addAll(report.circuits());
        issueRows.addAll(report.issues());
    }

    public void clear() {
        showReport(null);
        summaryLabel.setText("Run Tools → Recalculate Loads to generate a design report.");
    }

    private static TableColumn<CircuitLoad, String> col(String title, java.util.function.Function<CircuitLoad, String> fn) {
        TableColumn<CircuitLoad, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(fn.apply(cd.getValue())));
        return c;
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
