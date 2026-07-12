package com.ghana.gwire.ui;

import com.ghana.gwire.GWireApp;
import com.ghana.gwire.ai.AiDesignPlan;
import com.ghana.gwire.ai.AiDesignService;
import com.ghana.gwire.ai.AiSettings;
import com.ghana.gwire.ai.vision.VisionFloorPlanResult;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.calc.CalcEngine;
import com.ghana.gwire.service.persist.ProjectStore;
import com.ghana.gwire.ui.canvas.DrawTool;
import com.ghana.gwire.ui.canvas.FloorPlanWorkspace;
import com.ghana.gwire.ui.menu.AppMenuBar;
import com.ghana.gwire.ui.panels.BoqPanel;
import com.ghana.gwire.ui.panels.CalcResultsPanel;
import com.ghana.gwire.ui.panels.PropertiesPanel;
import com.ghana.gwire.ui.panels.StatusBar;
import com.ghana.gwire.ui.panels.SymbolLibraryPanel;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

/**
 * Primary application chrome: library, floor-plan, properties, calc, BOQ, status.
 */
public class MainWindow {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    private final Stage stage;
    private final ThemeManager themeManager;
    private final BorderPane root;
    private final StatusBar statusBar;
    private final FloorPlanWorkspace workspace;
    private final PropertiesPanel propertiesPanel;
    private final SymbolLibraryPanel symbolLibraryPanel;
    private final CalcResultsPanel calcResultsPanel;
    private final BoqPanel boqPanel;
    private final AppMenuBar menuBar;
    private final CalcEngine calcEngine = new CalcEngine();
    private final ProjectStore projectStore = new ProjectStore();

    private Project project;
    private Path projectPath;
    private boolean dirty;

    public MainWindow(Stage stage, ThemeManager themeManager) {
        this.stage = stage;
        this.themeManager = themeManager;
        this.statusBar = new StatusBar();
        this.workspace = new FloorPlanWorkspace();
        this.propertiesPanel = new PropertiesPanel();
        this.symbolLibraryPanel = new SymbolLibraryPanel();
        this.calcResultsPanel = new CalcResultsPanel();
        this.boqPanel = new BoqPanel();
        this.menuBar = new AppMenuBar(this);

        workspace.setOwnerWindow(stage);
        workspace.setStatusSink(statusBar::setMessage);
        workspace.setSelectionListener(this::refreshSelection);
        workspace.setModelChangeListener(this::onModelChanged);

        propertiesPanel.setOnProjectChanged(() -> {
            markDirty();
            refreshTitleAndStatus();
        });
        propertiesPanel.setOnGeometryChanged(() -> {
            workspace.getCanvas().redraw();
            onModelChanged();
        });

        symbolLibraryPanel.setStatusSink(statusBar::setMessage);

        SplitPane rightSplit = new SplitPane(
                propertiesPanel.getRoot(),
                calcResultsPanel.getRoot(),
                boqPanel.getRoot()
        );
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.28, 0.65);
        rightSplit.setPrefWidth(340);

        SplitPane centerSplit = new SplitPane(
                symbolLibraryPanel.getRoot(),
                workspace.getRoot(),
                rightSplit
        );
        centerSplit.setOrientation(Orientation.HORIZONTAL);
        centerSplit.setDividerPositions(0.20, 0.68);
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
                "Phase 6: File → Save/Open (.gwire) · AI design · Tools → Recalculate ("
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
        if (!confirmDiscardIfDirty()) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog("New residence");
        dialog.initOwner(stage);
        dialog.setTitle("New project");
        dialog.setHeaderText("Create a floor plan project");
        dialog.setContentText("Project name:");
        dialog.showAndWait().ifPresent(name -> createProject(name, true));
    }

    private void createProject(String name, boolean announce) {
        project = new Project(name);
        projectPath = null;
        dirty = false;
        workspace.bindProject(project);
        propertiesPanel.setProject(project);
        boqPanel.setProject(project);
        calcResultsPanel.clear();
        refreshTitleAndStatus();
        refreshSelection();
        if (announce) {
            statusBar.setMessage("New project: " + project.name());
        }
    }

    public void openProject() {
        if (!confirmDiscardIfDirty()) {
            return;
        }
        FileChooser chooser = projectChooser("Open GhanaWire project");
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            Project loaded = projectStore.load(file.toPath());
            project = loaded;
            projectPath = file.toPath();
            dirty = false;
            workspace.bindProject(project);
            propertiesPanel.setProject(project);
            boqPanel.setProject(project);
            calcResultsPanel.clear();
            workspace.getCanvas().fitToWindow();
            refreshTitleAndStatus();
            refreshSelection();
            statusBar.setMessage("Opened " + file.getName()
                    + " · " + project.floorPlan().rooms().size() + " room(s), "
                    + project.floorPlan().devices().size() + " device(s)");
        } catch (Exception ex) {
            log.error("Open failed", ex);
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(stage);
            err.setTitle("Open project");
            err.setHeaderText("Could not open project");
            err.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            err.showAndWait();
        }
    }

    public void saveProject() {
        if (project == null) {
            statusBar.setMessage("No project to save.");
            return;
        }
        if (projectPath == null) {
            saveProjectAs();
            return;
        }
        writeProjectTo(projectPath);
    }

    public void saveProjectAs() {
        if (project == null) {
            statusBar.setMessage("No project to save.");
            return;
        }
        FileChooser chooser = projectChooser("Save GhanaWire project");
        if (projectPath != null) {
            chooser.setInitialDirectory(projectPath.getParent() == null
                    ? null : projectPath.getParent().toFile());
            chooser.setInitialFileName(projectPath.getFileName().toString());
        } else {
            chooser.setInitialFileName(sanitizeFileName(project.name()) + ".gwire");
        }
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".gwire")) {
            path = path.resolveSibling(path.getFileName().toString() + ".gwire");
        }
        writeProjectTo(path);
    }

    private void writeProjectTo(Path path) {
        try {
            projectStore.save(project, path);
            projectPath = path;
            dirty = false;
            refreshTitleAndStatus();
            statusBar.setMessage("Saved " + path.getFileName());
        } catch (Exception ex) {
            log.error("Save failed", ex);
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(stage);
            err.setTitle("Save project");
            err.setHeaderText("Could not save project");
            err.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            err.showAndWait();
        }
    }

    private FileChooser projectChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("GhanaWire project (*.gwire)", "*.gwire"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        return chooser;
    }

    private boolean confirmDiscardIfDirty() {
        if (!dirty) {
            return true;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Unsaved changes");
        confirm.setHeaderText("Project has unsaved changes");
        confirm.setContentText("Discard changes and continue?");
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        var choice = confirm.showAndWait();
        return choice.isPresent() && choice.get() == ButtonType.YES;
    }

    private void markDirty() {
        dirty = true;
        if (project != null) {
            project.touch();
        }
        refreshTitleAndStatus();
    }

    private void onModelChanged() {
        markDirty();
        boqPanel.refresh();
        // Invalidate stale calc report when geometry/devices change
        if (project != null && project.lastReport() != null) {
            project.setLastReport(null);
            calcResultsPanel.clear();
        }
        refreshTitleAndStatus();
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "project";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]+", "_");
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

    /**
     * Runs the full design calculation (loads, diversity, cable size, validation).
     */
    public void recalculateLoads() {
        if (project == null) {
            statusBar.setMessage("No project to calculate.");
            return;
        }
        try {
            DesignReport report = calcEngine.calculate(project, LibraryBootstrap.get());
            project.setLastReport(report);
            calcResultsPanel.showReport(report);
            boqPanel.refresh();
            refreshTitleAndStatus();
            statusBar.setMessage(String.format(
                    "Calculation complete · %.0f W after diversity · %.1f A · %d circuit(s) · %d error(s), %d warning(s)",
                    report.totalAfterDiversityW(),
                    report.totalDesignCurrentA(),
                    report.circuits().size(),
                    report.errorCount(),
                    report.warningCount()
            ));
            log.info("Calc report: {}", report);
        } catch (Exception ex) {
            log.error("Calculation failed", ex);
            statusBar.setMessage("Calculation failed: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("Calculation failed");
            alert.setHeaderText("Could not recalculate loads");
            alert.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Same engine as recalculate; focuses user on validation outcomes.
     */
    public void validateStandards() {
        recalculateLoads();
        DesignReport report = project == null ? null : project.lastReport();
        if (report == null) {
            return;
        }
        Alert alert = new Alert(report.hasErrors() ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("Standards validation (L.I. 2008 practice)");
        alert.setHeaderText(String.format(
                "%d error(s), %d warning(s) · max Vd %.2f%%",
                report.errorCount(),
                report.warningCount(),
                report.maxVoltageDropPercent()
        ));
        StringBuilder body = new StringBuilder();
        body.append("Illustrative checks for preliminary design — verify with a CEWP.\n\n");
        int shown = 0;
        for (var issue : report.issues()) {
            if (issue.severity() == com.ghana.gwire.domain.calc.Severity.INFO && shown > 8) {
                continue;
            }
            body.append("• [").append(issue.severity()).append("] ")
                    .append(issue.code()).append(": ")
                    .append(issue.message()).append('\n');
            shown++;
            if (shown >= 20) {
                body.append("… see Calculation panel for full list.\n");
                break;
            }
        }
        if (report.issues().isEmpty()) {
            body.append("No issues raised for the current layout.");
        }
        alert.setContentText(body.toString());
        alert.showAndWait();
    }

    /**
     * Phase 5: generate placements (LLM if configured, else rules) and apply to the floor plan.
     */
    public void aiGenerateDesign() {
        runAiGenerate(false);
    }

    /** Force offline rule-based placement (no LLM call). */
    public void aiGenerateDesignRulesOnly() {
        runAiGenerate(true);
    }

    /**
     * Vision-analyse the imported floor-plan background and create rooms/walls.
     * Requires File → Import Floor Plan first. Uses LLM vision if API key set;
     * otherwise places one offline full-plan room fallback.
     */
    public void analyzeFloorPlanVision() {
        if (project == null) {
            statusBar.setMessage("No project.");
            return;
        }
        if (project.floorPlan().background() == null) {
            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.initOwner(stage);
            warn.setTitle("Vision analysis");
            warn.setHeaderText("No floor-plan image imported");
            warn.setContentText(
                    "Import a plan first (File → Import Floor Plan or toolbar Import plan…),\n"
                            + "then run Design → Analyze Floor Plan (Vision)."
            );
            warn.showAndWait();
            return;
        }

        boolean clearGeom = true;
        if (!project.floorPlan().rooms().isEmpty() || !project.floorPlan().walls().isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(stage);
            confirm.setTitle("Vision analysis");
            confirm.setHeaderText("Replace existing rooms/walls?");
            confirm.setContentText(
                    "Yes = clear rooms, walls, and openings, then apply vision result.\n"
                            + "No = append detected rooms/walls.\n"
                            + "Cancel = abort."
            );
            confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            var choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                return;
            }
            clearGeom = choice.get() == ButtonType.YES;
        }

        try {
            statusBar.setMessage("Analysing floor plan with vision…");
            workspace.getHistory().push(project.floorPlan());
            AiDesignService ai = new AiDesignService(AiSettings.load());
            var result = ai.analyzeAndApplyVision(project, clearGeom, false);
            if (result.isEmpty()) {
                statusBar.setMessage("Vision analysis failed.");
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.initOwner(stage);
                err.setTitle("Vision analysis");
                err.setHeaderText("Could not analyse floor plan");
                err.setContentText(
                        "Check that the background image file still exists on disk.\n"
                                + "For full room detection set GWIRE_AI_API_KEY and a vision model "
                                + "(e.g. gpt-4o-mini). Offline mode only creates one covering room."
                );
                err.showAndWait();
                return;
            }
            VisionFloorPlanResult r = result.get();
            workspace.getCanvas().fitToWindow();
            workspace.getCanvas().redraw();
            boqPanel.refresh();
            if (project.lastReport() != null) {
                project.setLastReport(null);
                calcResultsPanel.clear();
            }
            refreshTitleAndStatus();
            statusBar.setMessage("Vision: " + r.summary() + " · Ctrl+Z to undo");
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(stage);
            info.setTitle("Vision analysis");
            info.setHeaderText(r.summary());
            String body = r.notes()
                    + "\n\nNext: Design → AI Generate Design to place electrical devices, "
                    + "or Design → Vision + AI Design (full) to do both.";
            if (body.length() > 1400) {
                body = body.substring(0, 1400) + "\n…";
            }
            info.setContentText(body);
            info.showAndWait();
        } catch (Exception ex) {
            log.error("Vision analysis failed", ex);
            statusBar.setMessage("Vision failed: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("Vision analysis failed");
            alert.setHeaderText("Could not analyse floor plan");
            alert.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Vision rooms from background, then electrical AI design placements.
     */
    public void visionThenAiDesign() {
        if (project == null) {
            statusBar.setMessage("No project.");
            return;
        }
        if (project.floorPlan().background() == null) {
            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.initOwner(stage);
            warn.setTitle("Vision + AI Design");
            warn.setHeaderText("No floor-plan image imported");
            warn.setContentText("Import a plan first, then run this command.");
            warn.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Vision + AI Design");
        confirm.setHeaderText("Replace rooms/walls and devices?");
        confirm.setContentText(
                "This will re-detect rooms from the imported plan image and place electrical devices.\n"
                        + "Existing rooms, walls, openings, and devices will be replaced.\n\n"
                        + "Continue?"
        );
        var go = confirm.showAndWait();
        if (go.isEmpty() || go.get() != ButtonType.OK) {
            return;
        }

        try {
            statusBar.setMessage("Vision + AI design pipeline running…");
            workspace.getHistory().push(project.floorPlan());
            AiDesignService ai = new AiDesignService(AiSettings.load());
            int devices = ai.generateFromVisionBackground(project, LibraryBootstrap.get(), true, true);
            if (devices < 0) {
                statusBar.setMessage("Vision + AI design failed (no analysis result).");
                return;
            }
            workspace.getCanvas().fitToWindow();
            workspace.getCanvas().redraw();
            boqPanel.refresh();
            if (project.lastReport() != null) {
                project.setLastReport(null);
                calcResultsPanel.clear();
            }
            refreshTitleAndStatus();
            statusBar.setMessage(String.format(
                    "Vision + AI design: %d room(s), %d device(s) · Ctrl+Z to undo",
                    project.floorPlan().rooms().size(), devices
            ));
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(stage);
            info.setTitle("Vision + AI Design");
            info.setHeaderText(String.format(
                    "%d room(s) · %d device(s)",
                    project.floorPlan().rooms().size(), devices
            ));
            info.setContentText(
                    "Rooms detected from the imported plan; electrical devices placed.\n"
                            + "Run Tools → Recalculate Loads for cable sizing and L.I. 2008 checks."
            );
            info.showAndWait();
        } catch (Exception ex) {
            log.error("Vision + AI design failed", ex);
            statusBar.setMessage("Vision + AI design failed: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("Vision + AI Design failed");
            alert.setHeaderText("Pipeline error");
            alert.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            alert.showAndWait();
        }
    }

    private void runAiGenerate(boolean rulesOnly) {
        if (project == null) {
            statusBar.setMessage("No project for AI design.");
            return;
        }
        if (project.floorPlan().rooms().isEmpty()) {
            Alert warn = new Alert(Alert.AlertType.WARNING);
            warn.initOwner(stage);
            warn.setTitle("AI Generate Design");
            warn.setHeaderText("No rooms on the floor plan");
            warn.setContentText("Draw rooms first (Room tool), then run AI Generate Design.");
            warn.showAndWait();
            return;
        }

        boolean clear = true;
        if (!project.floorPlan().devices().isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(stage);
            confirm.setTitle("AI Generate Design");
            confirm.setHeaderText("Replace existing devices?");
            confirm.setContentText(
                    "Yes = clear current devices and apply the new plan.\n"
                            + "No = append placements.\n"
                            + "Cancel = abort."
            );
            confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            var choice = confirm.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                return;
            }
            clear = choice.get() == ButtonType.YES;
        }

        try {
            statusBar.setMessage(rulesOnly
                    ? "Generating design with offline rules…"
                    : "Generating design (LLM if configured, else rules)…");
            AiDesignService ai = new AiDesignService(AiSettings.load());
            AiDesignPlan plan = rulesOnly
                    ? ai.generateRulesOnly(project, LibraryBootstrap.get())
                    : ai.generate(project, LibraryBootstrap.get());
            // Snapshot for undo
            workspace.getHistory().push(project.floorPlan());
            int n = ai.apply(project, plan, clear);
            workspace.getCanvas().redraw();
            onModelChanged();
            refreshSelection();
            statusBar.setMessage(String.format(
                    "AI design applied: %d device(s) · source=%s · %s · Ctrl+Z to undo",
                    n, plan.source(), plan.providerDetail()
            ));
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(stage);
            info.setTitle("AI Generate Design");
            info.setHeaderText(String.format("%d placements · %s", n, plan.source()));
            String body = plan.notes()
                    + "\n\nTip: Tools → Recalculate Loads to size cables and validate L.I. 2008 checks.";
            if (body.length() > 1400) {
                body = body.substring(0, 1400) + "\n…";
            }
            info.setContentText(body);
            info.showAndWait();
        } catch (Exception ex) {
            log.error("AI design failed", ex);
            statusBar.setMessage("AI design failed: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("AI design failed");
            alert.setHeaderText("Could not generate design");
            alert.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Minimal co-pilot prompt (rule-based commands; optional LLM free-text when configured).
     */
    public void aiCopilotChat() {
        if (project == null) {
            statusBar.setMessage("No project for co-pilot.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        dialog.setTitle("AI Co-pilot");
        dialog.setHeaderText("GhanaWire co-pilot (examples: add socket in Living, recalculate)");
        dialog.setContentText("Command:");
        dialog.showAndWait().ifPresent(msg -> {
            try {
                workspace.getHistory().push(project.floorPlan());
                AiDesignService ai = new AiDesignService(AiSettings.load());
                String reply = ai.coPilot(project, LibraryBootstrap.get(), msg);
                workspace.getCanvas().redraw();
                boqPanel.refresh();
                refreshTitleAndStatus();
                statusBar.setMessage("Co-pilot: " + (reply.length() > 80 ? reply.substring(0, 80) + "…" : reply));
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.initOwner(stage);
                info.setTitle("AI Co-pilot");
                info.setHeaderText("Reply");
                info.setContentText(reply);
                info.showAndWait();
            } catch (Exception ex) {
                log.warn("Co-pilot failed: {}", ex.getMessage());
                statusBar.setMessage("Co-pilot failed: " + ex.getMessage());
            }
        });
    }

    public void setTool(DrawTool tool) {
        workspace.setTool(tool);
    }

    public void undo() {
        workspace.getCanvas().undo();
        onModelChanged();
        refreshSelection();
    }

    public void redo() {
        workspace.getCanvas().redo();
        onModelChanged();
        refreshSelection();
    }

    public void deleteSelection() {
        workspace.getCanvas().deleteSelection();
        onModelChanged();
        refreshSelection();
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

                Phase 5: AI design (rules + optional LLM), loads, standards checks.
                Catalogue: %d items. AI: %s
                """.formatted(
                        GWireApp.APP_VERSION,
                        n,
                        AiSettings.load().isLlmAvailable() ? "LLM ready" : "rules only (no API key)"
                )
        );
        about.showAndWait();
    }

    public void quit() {
        stage.close();
    }

    private void refreshSelection() {
        propertiesPanel.showSelection(workspace.getSelection());
        boqPanel.refresh();
        if (project != null && project.lastReport() != null) {
            calcResultsPanel.showReport(project.lastReport());
        }
        refreshTitleAndStatus();
    }

    private void refreshTitleAndStatus() {
        if (project == null) {
            stage.setTitle(GWireApp.APP_NAME + " — " + GWireApp.APP_SHORT_NAME);
            statusBar.setSecondary("No project");
            return;
        }
        String dirtyMark = dirty ? " *" : "";
        String pathHint = projectPath == null ? "" : " — " + projectPath.getFileName();
        stage.setTitle(GWireApp.APP_NAME + " — " + project.name() + pathHint + dirtyMark);
        String secondary = "L.I. 2008 · " + project.supplySummary()
                + " · walls " + project.floorPlan().walls().size()
                + " · rooms " + project.floorPlan().rooms().size()
                + " · devices " + project.floorPlan().devices().size();
        DesignReport report = project.lastReport();
        if (report != null) {
            secondary += String.format(
                    " · %.0f W (div) · %.1f A · Vd max %.1f%%",
                    report.totalAfterDiversityW(),
                    report.totalDesignCurrentA(),
                    report.maxVoltageDropPercent()
            );
        } else if (dirty) {
            secondary += " · unsaved";
        }
        statusBar.setSecondary(secondary);
    }
}
