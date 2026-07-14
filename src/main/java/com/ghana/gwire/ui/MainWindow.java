package com.ghana.gwire.ui;

import com.ghana.gwire.GWireApp;
import com.ghana.gwire.ai.AiDesignPlan;
import com.ghana.gwire.ai.AiDesignService;
import com.ghana.gwire.ai.AiPreviewSession;
import com.ghana.gwire.ai.AiSettings;
import com.ghana.gwire.ai.vision.VisionFloorPlanResult;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.cad.CadCommandParser;
import com.ghana.gwire.service.calc.CalcEngine;
import com.ghana.gwire.service.calc.CalcSessionState;
import com.ghana.gwire.service.prefs.UserPrefs;
import com.ghana.gwire.service.telemetry.TelemetryService;
import com.ghana.gwire.service.update.UpdateCheckService;
import com.ghana.gwire.samples.SampleProjectFactory;
import com.ghana.gwire.service.export.BoqExcelExportService;
import com.ghana.gwire.service.export.PdfExportService;
import com.ghana.gwire.service.persist.AutosaveService;
import com.ghana.gwire.service.persist.ProjectStore;
import com.ghana.gwire.service.sld.SingleLineDiagram;
import com.ghana.gwire.service.sld.SingleLineDiagramBuilder;
import com.ghana.gwire.service.wiring.WiringRouteService;
import com.ghana.gwire.ui.canvas.DrawTool;
import com.ghana.gwire.ui.canvas.FloorPlanWorkspace;
import com.ghana.gwire.ui.dialogs.PriceBookDialog;
import com.ghana.gwire.ui.menu.AppMenuBar;
import com.ghana.gwire.ui.panels.BoqPanel;
import com.ghana.gwire.ui.panels.CalcResultsPanel;
import com.ghana.gwire.ui.panels.ElectricalPanel;
import com.ghana.gwire.ui.panels.PropertiesPanel;
import com.ghana.gwire.ui.panels.StatusBar;
import com.ghana.gwire.ui.panels.SymbolLibraryPanel;
import com.ghana.gwire.ui.theme.ThemeManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Primary application chrome: library, floor-plan, properties, calc, BOQ, status.
 */
public class MainWindow {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);

    private final Stage stage;
    private final ThemeManager themeManager;
    private final UserPrefs userPrefs;
    private final BorderPane root;
    private final StatusBar statusBar;
    private final FloorPlanWorkspace workspace;
    private final PropertiesPanel propertiesPanel;
    private final SymbolLibraryPanel symbolLibraryPanel;
    private final CalcResultsPanel calcResultsPanel;
    private final ElectricalPanel electricalPanel;
    private final BoqPanel boqPanel;
    private final AppMenuBar menuBar;
    private final CalcEngine calcEngine = new CalcEngine();
    private final ProjectStore projectStore = new ProjectStore();
    private final AutosaveService autosaveService = new AutosaveService(projectStore);
    private final PdfExportService pdfExportService = new PdfExportService();
    private final BoqExcelExportService boqExcelExportService = new BoqExcelExportService();
    private final WiringRouteService wiringRouteService = new WiringRouteService();
    private final SingleLineDiagramBuilder sldBuilder = new SingleLineDiagramBuilder();
    private Timeline autosaveTimeline;

    private Project project;
    private Path projectPath;
    private boolean dirty;
    /** Session-only calc freshness (not persisted). */
    private CalcSessionState calcState = CalcSessionState.NONE;
    private long projectModelRevision;
    private long lastCalcModelRevision = -1;
    private boolean exportWithErrorsAcknowledged;

    public MainWindow(Stage stage, ThemeManager themeManager) {
        this(stage, themeManager, new UserPrefs());
    }

    public MainWindow(Stage stage, ThemeManager themeManager, UserPrefs userPrefs) {
        this.stage = stage;
        this.themeManager = themeManager;
        this.userPrefs = userPrefs == null ? new UserPrefs() : userPrefs;
        this.statusBar = new StatusBar();
        this.workspace = new FloorPlanWorkspace();
        this.propertiesPanel = new PropertiesPanel();
        this.symbolLibraryPanel = new SymbolLibraryPanel();
        this.calcResultsPanel = new CalcResultsPanel();
        this.electricalPanel = new ElectricalPanel();
        this.boqPanel = new BoqPanel();
        this.menuBar = new AppMenuBar(this);

        workspace.setOwnerWindow(stage);
        workspace.setStatusSink(statusBar::setMessage);
        workspace.setSelectionListener(this::refreshSelection);
        workspace.setModelChangeListener(this::onModelChanged);
        workspace.setStoreyChangeListener(() -> {
            refreshSelection();
            markDirty();
            refreshTitleAndStatus();
        });

        propertiesPanel.setOnProjectChanged(() -> {
            markDirty();
            refreshTitleAndStatus();
        });
        propertiesPanel.setOnGeometryChanged(() -> {
            workspace.getCanvas().redraw();
            onModelChanged();
        });

        symbolLibraryPanel.setStatusSink(statusBar::setMessage);
        electricalPanel.setStatusSink(statusBar::setMessage);
        electricalPanel.setModelChanged(this::onModelChanged);
        statusBar.setCommandHandler(this::handleCadCommand);
        TelemetryService.get().setEnabled(this.userPrefs.isTelemetryOptIn());
        TelemetryService.get().record(TelemetryService.EVENT_APP_START);

        SplitPane rightSplit = new SplitPane(
                propertiesPanel.getRoot(),
                calcResultsPanel.getRoot(),
                electricalPanel.getRoot(),
                boqPanel.getRoot()
        );
        rightSplit.setOrientation(Orientation.VERTICAL);
        rightSplit.setDividerPositions(0.22, 0.48, 0.75);
        rightSplit.setPrefWidth(360);

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

        stage.setOnCloseRequest(this::onCloseRequest);

        createProject("Untitled project", false);
        startAutosaveTimer();
        int count = 0;
        try {
            if (LibraryBootstrap.get() != null) {
                count = LibraryBootstrap.get().count();
            }
        } catch (Exception ignored) {
            // library optional at UI build time
        }
        statusBar.setMessage(
                "Ready · Help → Open Sample 3-Bed House · File → Export PDF/Excel ("
                        + count + " catalogue items)."
        );
        statusBar.setSecondary(GWireApp.standardsStamp() + " · 230 V / 50 Hz");
    }

    /** Background HTTPS version check (non-blocking banner). */
    public void startBackgroundUpdateCheck() {
        new UpdateCheckService(userPrefs).checkAsync().thenAccept(opt -> {
            if (opt.isEmpty()) {
                return;
            }
            UpdateCheckService.UpdateInfo info = opt.get();
            Platform.runLater(() -> {
                statusBar.setSecondary(
                        "Update available: " + info.latestVersion()
                                + " — " + info.releaseUrl()
                                + "  ·  " + GWireApp.standardsStamp()
                );
                statusBar.setMessage(
                        "A newer version (" + info.latestVersion() + ") is available. See status bar for link."
                );
            });
        });
    }

    private void startAutosaveTimer() {
        // Every 5 minutes when dirty — full multi-storey project
        autosaveTimeline = new Timeline(new KeyFrame(Duration.minutes(5), e -> maybeAutosave()));
        autosaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autosaveTimeline.play();
    }

    private void maybeAutosave() {
        if (!dirty || project == null) {
            return;
        }
        try {
            autosaveService.autosave(project);
            log.debug("Autosaved project {}", project.id());
        } catch (Exception ex) {
            log.warn("Autosave failed: {}", ex.getMessage());
        }
    }

    /**
     * Offer recovery of crash autosave (called once after stage is shown).
     */
    public void checkCrashRecovery() {
        Optional<Path> candidate = autosaveService.recoveryCandidate();
        autosaveService.clearCleanExitMarker();
        if (candidate.isEmpty()) {
            return;
        }
        Path path = candidate.get();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Recover project");
        alert.setHeaderText("An autosaved project was found after an unexpected exit.");
        alert.setContentText("Recover \"" + path.getFileName() + "\"?");
        ButtonType recover = new ButtonType("Recover", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(recover, discard);
        var choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == recover) {
            try {
                Project loaded = autosaveService.loadAutosave(path);
                project = loaded;
                projectPath = null;
                dirty = true;
                resetCalcSession();
                workspace.bindProject(project);
                propertiesPanel.setProject(project);
                boqPanel.setProject(project);
                electricalPanel.setProject(project);
                calcResultsPanel.clear();
                workspace.getCanvas().fitToWindow();
                refreshTitleAndStatus();
                refreshSelection();
                statusBar.setMessage("Recovered autosave — save the project to keep it.");
            } catch (Exception ex) {
                log.error("Recovery failed", ex);
                ErrorDialog.show(stage, "Could not recover autosave", ex);
            }
        } else {
            // Discard all autosaves from unclean session
            for (Path p : autosaveService.listAutosaves()) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
    }

    private void onCloseRequest(WindowEvent e) {
        if (!promptUnsavedChanges()) {
            e.consume();
            return;
        }
        performCleanExit();
    }

    /** Graceful shutdown: clean-exit marker (autosave already handled by prompt). */
    public void performCleanExit() {
        if (autosaveTimeline != null) {
            autosaveTimeline.stop();
        }
        autosaveService.writeCleanExitMarker();
    }

    /**
     * @return true if caller may proceed (saved, discarded, or not dirty); false = cancel
     */
    private boolean promptUnsavedChanges() {
        if (!dirty) {
            return true;
        }
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType dontSave = new ButtonType("Don't save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(stage);
        confirm.setTitle("Unsaved changes");
        confirm.setHeaderText("Project has unsaved changes");
        confirm.setContentText("Save before continuing?");
        confirm.getButtonTypes().setAll(save, dontSave, cancel);
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            return false;
        }
        if (choice.get() == save) {
            if (projectPath == null) {
                if (!saveProjectAsReturningSuccess()) {
                    return false; // chooser cancelled → abort close
                }
            } else if (!writeProjectToReturningSuccess(projectPath)) {
                return false;
            }
            if (project != null) {
                autosaveService.deleteAutosave(project.id());
            }
            return true;
        }
        // Don't save
        if (project != null) {
            autosaveService.deleteAutosave(project.id());
        }
        return true;
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
        if (!promptUnsavedChanges()) {
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
        resetCalcSession();
        workspace.bindProject(project);
        propertiesPanel.setProject(project);
        boqPanel.setProject(project);
        electricalPanel.setProject(project);
        calcResultsPanel.clear();
        refreshTitleAndStatus();
        refreshSelection();
        if (announce) {
            statusBar.setMessage("New project: " + project.name());
        }
    }

    private void resetCalcSession() {
        calcState = CalcSessionState.NONE;
        projectModelRevision = 0;
        lastCalcModelRevision = -1;
        exportWithErrorsAcknowledged = false;
        calcResultsPanel.setSessionState(calcState, null);
    }

    private void markCalcFresh(DesignReport report) {
        lastCalcModelRevision = projectModelRevision;
        exportWithErrorsAcknowledged = false;
        if (report != null && report.hasErrors()) {
            calcState = CalcSessionState.ERRORS_PRESENT;
        } else {
            calcState = CalcSessionState.FRESH;
        }
        calcResultsPanel.setSessionState(calcState, report);
    }

    /**
     * @return false if the user cancelled export
     */
    private boolean ensureCalcReadyForExport(boolean needCables) {
        if (project == null) {
            return false;
        }
        if (calcState == CalcSessionState.DIRTY_CLEARED
                || (needCables && calcState == CalcSessionState.NONE)
                || (needCables && project.lastReport() == null)) {
            if (calcState == CalcSessionState.DIRTY_CLEARED) {
                Alert ask = new Alert(Alert.AlertType.CONFIRMATION);
                ask.initOwner(stage);
                ask.setTitle("Calculations outdated");
                ask.setHeaderText("Calculations outdated — recalculate?");
                ask.setContentText("The design changed since the last calculation. Recalculate before export?");
                ask.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                var choice = ask.showAndWait();
                if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                    return false;
                }
                if (choice.get() == ButtonType.NO && !needCables) {
                    return true;
                }
            }
            try {
                DesignReport report = calcEngine.calculate(project, LibraryBootstrap.get());
                report.setCalculatedAtExport(true);
                project.setLastReport(report);
                markCalcFresh(report);
                calcResultsPanel.showReport(report);
                statusBar.setMessage("Calculated at export · " + report.calculatedAt());
            } catch (Exception ex) {
                ErrorDialog.show(stage, "Calculation failed", ex);
                return false;
            }
        }
        if (calcState == CalcSessionState.ERRORS_PRESENT
                || (project.lastReport() != null && project.lastReport().hasErrors())) {
            if (!exportWithErrorsAcknowledged) {
                Alert ask = new Alert(Alert.AlertType.CONFIRMATION);
                ask.initOwner(stage);
                ask.setTitle("Validation errors");
                ask.setHeaderText("Export with validation errors?");
                int n = project.lastReport() == null ? 0 : project.lastReport().errorCount();
                ask.setContentText(n + " error(s) present. A CEWP should resolve these before installation.");
                ButtonType exportAnyway = new ButtonType("Export with errors", ButtonBar.ButtonData.YES);
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                ask.getButtonTypes().setAll(exportAnyway, cancel);
                var choice = ask.showAndWait();
                if (choice.isEmpty() || choice.get() == cancel) {
                    return false;
                }
                exportWithErrorsAcknowledged = true;
            }
        }
        return true;
    }

    public void openProject() {
        if (!promptUnsavedChanges()) {
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
            resetCalcSession();
            workspace.bindProject(project);
            propertiesPanel.setProject(project);
            boqPanel.setProject(project);
            electricalPanel.setProject(project);
            calcResultsPanel.clear();
            workspace.getCanvas().fitToWindow();
            refreshTitleAndStatus();
            refreshSelection();
            statusBar.setMessage("Opened " + file.getName()
                    + " · " + project.totalRoomCount() + " room(s), "
                    + project.totalDeviceCount() + " device(s)");
        } catch (Exception ex) {
            log.error("Open failed", ex);
            ErrorDialog.show(stage, "Could not open project", ex);
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
        writeProjectToReturningSuccess(projectPath);
    }

    public void saveProjectAs() {
        saveProjectAsReturningSuccess();
    }

    /** Save As; returns false if user cancelled or write failed. */
    private boolean saveProjectAsReturningSuccess() {
        if (project == null) {
            statusBar.setMessage("No project to save.");
            return false;
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
            return false;
        }
        Path path = file.toPath();
        String lower = path.getFileName().toString().toLowerCase();
        if (!lower.endsWith(".gwire") && !lower.endsWith(".gwirez")) {
            path = path.resolveSibling(path.getFileName().toString() + ".gwire");
        }
        return writeProjectToReturningSuccess(path);
    }

    /** File → Save as package (.gwirez) with embedded media. */
    public void saveProjectAsPackage() {
        if (project == null) {
            statusBar.setMessage("No project to save.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save as package");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("GhanaWire package (*.gwirez)", "*.gwirez")
        );
        chooser.setInitialFileName(sanitizeFileName(project.name()) + ".gwirez");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".gwirez")) {
            path = path.resolveSibling(path.getFileName().toString() + ".gwirez");
        }
        writeProjectToReturningSuccess(path);
    }

    private boolean writeProjectToReturningSuccess(Path path) {
        try {
            projectStore.save(project, path);
            projectPath = path;
            dirty = false;
            autosaveService.deleteAutosave(project.id());
            refreshTitleAndStatus();
            statusBar.setMessage("Saved " + path.getFileName());
            return true;
        } catch (Exception ex) {
            log.error("Save failed", ex);
            ErrorDialog.show(stage, "Could not save project", ex);
            return false;
        }
    }

    private FileChooser projectChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("GhanaWire project (*.gwire, *.gwirez)",
                        "*.gwire", "*.gwirez"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        return chooser;
    }

    private void markDirty() {
        dirty = true;
        if (project != null) {
            project.touch();
        }
        refreshTitleAndStatus();
    }

    private void onModelChanged() {
        if (project == null) {
            return;
        }
        dirty = true;
        project.touch();
        projectModelRevision++;
        boqPanel.refresh();
        // Invalidate stale calc report when geometry/devices change (idempotent)
        if (project.lastReport() != null) {
            project.setLastReport(null);
            calcResultsPanel.clear();
            calcState = CalcSessionState.DIRTY_CLEARED;
            exportWithErrorsAcknowledged = false;
            calcResultsPanel.setSessionState(calcState, null);
        } else if (calcState == CalcSessionState.FRESH || calcState == CalcSessionState.ERRORS_PRESENT) {
            calcState = CalcSessionState.DIRTY_CLEARED;
            calcResultsPanel.setSessionState(calcState, null);
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

    /**
     * Export Bill of Quantities only as an Excel workbook (.xlsx).
     */
    public void exportBoqExcel() {
        if (project == null) {
            statusBar.setMessage("No project to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export BOQ as Excel");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel workbook (*.xlsx)", "*.xlsx")
        );
        chooser.setInitialFileName(sanitizeFileName(project.name()) + "-boq.xlsx");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".xlsx")) {
            path = path.resolveSibling(path.getFileName().toString() + ".xlsx");
        }

        boolean includeCables = true;
        if (project.lastReport() == null || calcState == CalcSessionState.NONE) {
            Alert ask = new Alert(Alert.AlertType.CONFIRMATION);
            ask.initOwner(stage);
            ask.setTitle("Export BOQ (Excel)");
            ask.setHeaderText("Include estimated circuit cables?");
            ask.setContentText(
                    "Yes = run load calculation first and add cable length estimates.\n"
                            + "No = export placed devices only."
            );
            ask.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            var choice = ask.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                return;
            }
            includeCables = choice.get() == ButtonType.YES;
        }
        if (!ensureCalcReadyForExport(includeCables)) {
            return;
        }

        try {
            statusBar.setMessage("Exporting BOQ Excel…");
            boqExcelExportService.export(project, path, includeCables);
            if (project.lastReport() != null) {
                calcResultsPanel.showReport(project.lastReport());
            }
            boqPanel.refresh();
            refreshTitleAndStatus();
            statusBar.setMessage("Exported BOQ Excel: " + path.getFileName());
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(stage);
            info.setTitle("BOQ Excel export");
            info.setHeaderText("Workbook saved");
            info.setContentText(path.toAbsolutePath().toString());
            info.showAndWait();
        } catch (Exception ex) {
            log.error("BOQ Excel export failed", ex);
            statusBar.setMessage("BOQ Excel export failed: " + ex.getMessage());
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(stage);
            err.setTitle("BOQ Excel export");
            err.setHeaderText("Could not export Excel");
            err.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            err.showAndWait();
        }
    }

    /**
     * Export multi-page PDF: cover, floor plan, circuit schedule, BOQ, checklist.
     */
    public void exportPdfReport() {
        if (project == null) {
            statusBar.setMessage("No project to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PDF report");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf")
        );
        chooser.setInitialFileName(sanitizeFileName(project.name()) + "-report.pdf");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".pdf")) {
            path = path.resolveSibling(path.getFileName().toString() + ".pdf");
        }
        if (!ensureCalcReadyForExport(true)) {
            return;
        }
        try {
            statusBar.setMessage("Exporting PDF report…");
            if (project.lastReport() == null) {
                DesignReport report = calcEngine.calculate(project, LibraryBootstrap.get());
                report.setCalculatedAtExport(true);
                project.setLastReport(report);
                markCalcFresh(report);
                calcResultsPanel.showReport(report);
            }
            pdfExportService.export(project, path);
            TelemetryService.get().record(TelemetryService.EVENT_EXPORT_PDF);
            boqPanel.refresh();
            refreshTitleAndStatus();
            statusBar.setMessage("Exported PDF: " + path.getFileName());
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(stage);
            info.setTitle("PDF export");
            info.setHeaderText("Report saved");
            info.setContentText(
                    path.toAbsolutePath()
                            + "\n\nPages: cover, floor plan, circuit schedule, BOQ, compliance checklist.\n"
                            + "Preliminary design only — CEWP verification required."
            );
            info.showAndWait();
        } catch (Exception ex) {
            log.error("PDF export failed", ex);
            statusBar.setMessage("PDF export failed: " + ex.getMessage());
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.initOwner(stage);
            err.setTitle("PDF export");
            err.setHeaderText("Could not export PDF");
            err.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            err.showAndWait();
        }
    }

    public void showComponentLibrary() {
        symbolLibraryPanel.reload();
        statusBar.setMessage("Component library reloaded ("
                + (LibraryBootstrap.get() == null ? 0 : LibraryBootstrap.get().count())
                + " items)");
    }

    public void setShowWiringRoutes(boolean show) {
        if (project != null) {
            project.floorPlan().setShowWiringRoutes(show);
            workspace.getCanvas().redraw();
        }
    }

    public void generateWiringRoutes() {
        if (project == null) {
            statusBar.setMessage("No project.");
            return;
        }
        try {
            workspace.getHistory().push(project.floorPlan());
            int n = wiringRouteService.generateForActiveStorey(project);
            project.floorPlan().setShowWiringRoutes(true);
            workspace.getCanvas().redraw();
            onModelChanged();
            if (project.lastReport() != null) {
                calcResultsPanel.showReport(project.lastReport());
            }
            statusBar.setMessage("Generated " + n + " wiring route(s) on "
                    + project.activeStorey().displayLabel());
        } catch (Exception ex) {
            log.error("Wiring generation failed", ex);
            statusBar.setMessage("Wiring generation failed: " + ex.getMessage());
        }
    }

    public void showSingleLineDiagram() {
        if (project == null) {
            statusBar.setMessage("No project.");
            return;
        }
        try {
            SingleLineDiagram sld = sldBuilder.build(project);
            if (project.lastReport() != null) {
                calcResultsPanel.showReport(project.lastReport());
            }
            StringBuilder body = new StringBuilder();
            body.append(sld.title()).append("\n\n");
            appendSld(body, sld.root(), 0);
            body.append("\n").append(sld.notes());
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.initOwner(stage);
            info.setTitle("Single-line diagram");
            info.setHeaderText("Schematic SLD (also included in PDF export)");
            info.setContentText(body.length() > 3500 ? body.substring(0, 3500) + "\n..." : body.toString());
            info.getDialogPane().setPrefWidth(520);
            info.showAndWait();
            statusBar.setMessage("SLD preview shown · export PDF for printable page");
        } catch (Exception ex) {
            log.error("SLD failed", ex);
            statusBar.setMessage("SLD failed: " + ex.getMessage());
        }
    }

    private static void appendSld(StringBuilder sb, SingleLineDiagram.Node node, int depth) {
        sb.append("  ".repeat(depth)).append("+ ").append(node.kind()).append(": ")
                .append(node.label());
        if (!node.detail().isBlank()) {
            sb.append(" — ").append(node.detail());
        }
        sb.append('\n');
        for (SingleLineDiagram.Node c : node.children()) {
            appendSld(sb, c, depth + 1);
        }
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
            markCalcFresh(report);
            calcResultsPanel.showReport(report);
            electricalPanel.setProject(project);
            electricalPanel.refresh();
            boqPanel.refresh();
            refreshTitleAndStatus();
            TelemetryService.get().record(TelemetryService.EVENT_CALC_RUN);
            statusBar.setMessage(String.format(
                    "Calculation complete · %.0f W after diversity · %.1f A · %d circuit(s) · %d error(s), %d warning(s) · %s",
                    report.totalAfterDiversityW(),
                    report.totalDesignCurrentA(),
                    report.circuits().size(),
                    report.errorCount(),
                    report.warningCount(),
                    report.standardsEdition()
            ));
            log.info("Calc report: {} assumptions={}", report, report.assumptions().size());
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
            confirm.setHeaderText("Replace existing devices when accepting?");
            confirm.setContentText(
                    "Ghost preview will not change the plan until you Accept.\n\n"
                            + "Yes = clear current devices on accept.\n"
                            + "No = append selected placements on accept.\n"
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
            if (plan.isEmpty()) {
                statusBar.setMessage("AI returned no placements.");
                return;
            }
            // Non-destructive ghost preview (Phase 15)
            AiPreviewSession session = new AiPreviewSession(plan, clear);
            workspace.getCanvas().setAiPreview(session);
            workspace.getCanvas().setTool(DrawTool.SELECT);
            statusBar.setMessage(String.format(
                    "AI preview: %d ghost(s) · click to toggle · Accept/Reject in dialog · source=%s",
                    plan.size(), plan.source()
            ));

            ButtonType accept = new ButtonType("Accept selected", ButtonBar.ButtonData.OK_DONE);
            ButtonType reject = new ButtonType("Reject all", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType all = new ButtonType("Select all", ButtonBar.ButtonData.OTHER);
            ButtonType none = new ButtonType("Select none", ButtonBar.ButtonData.OTHER);
            Alert preview = new Alert(Alert.AlertType.CONFIRMATION);
            preview.initOwner(stage);
            preview.setTitle("AI design preview");
            preview.setHeaderText(String.format(
                    "%d placements · %s · click ghosts on canvas to multi-select",
                    plan.size(), plan.source()
            ));
            preview.setContentText(
                    (plan.notes() == null ? "" : plan.notes())
                            + "\n\nAccept applies only selected ghosts. Reject leaves the model untouched."
            );
            preview.getButtonTypes().setAll(accept, all, none, reject);

            // Keep dialog open for select all/none
            while (true) {
                var choice = preview.showAndWait();
                if (choice.isEmpty() || choice.get() == reject) {
                    workspace.getCanvas().clearAiPreview();
                    statusBar.setMessage("AI design rejected — model unchanged");
                    return;
                }
                if (choice.get() == all) {
                    session.selectAll();
                    workspace.getCanvas().setAiPreview(session);
                    preview.setHeaderText(String.format(
                            "%d/%d selected · %s",
                            session.selectedCount(), session.size(), plan.source()
                    ));
                    continue;
                }
                if (choice.get() == none) {
                    session.selectNone();
                    workspace.getCanvas().setAiPreview(session);
                    preview.setHeaderText(String.format(
                            "%d/%d selected · %s",
                            session.selectedCount(), session.size(), plan.source()
                    ));
                    continue;
                }
                if (choice.get() == accept) {
                    if (session.selectedCount() == 0) {
                        statusBar.setMessage("No placements selected — nothing applied");
                        workspace.getCanvas().clearAiPreview();
                        return;
                    }
                    workspace.getHistory().push(project.floorPlan());
                    int n = ai.apply(project, session.toFilteredPlan(), session.clearExistingDevices());
                    workspace.getCanvas().clearAiPreview();
                    workspace.getCanvas().redraw();
                    onModelChanged();
                    refreshSelection();
                    statusBar.setMessage(String.format(
                            "AI design accepted: %d device(s) · source=%s · Ctrl+Z to undo",
                            n, plan.source()
                    ));
                    return;
                }
            }
        } catch (Exception ex) {
            log.error("AI design failed", ex);
            workspace.getCanvas().clearAiPreview();
            statusBar.setMessage("AI design failed: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(stage);
            alert.setTitle("AI design failed");
            alert.setHeaderText("Could not generate design");
            alert.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            alert.showAndWait();
        }
    }

    /** CAD command line (Phase 15): LINE, length, ORTHO, OSNAP. */
    public void handleCadCommand(String raw) {
        CadCommandParser.Result r = CadCommandParser.parse(raw);
        switch (r.kind()) {
            case HELP -> statusBar.setMessage(
                    "Commands: LINE | 3500 | 3.5m | ORTHO ON/OFF | OSNAP ON/OFF | CANCEL | HELP"
            );
            case CANCEL -> {
                workspace.getCanvas().setTool(DrawTool.SELECT);
                statusBar.setMessage("Command cancelled");
            }
            case LINE -> {
                setTool(DrawTool.WALL);
                statusBar.setMessage("LINE: click start, then end — or click start and type length");
            }
            case ORTHO_ON -> {
                setOrthoMode(true);
                statusBar.setMessage("Ortho ON");
            }
            case ORTHO_OFF -> {
                setOrthoMode(false);
                statusBar.setMessage("Ortho OFF");
            }
            case OSNAP_ON -> {
                setEndpointSnap(true);
                statusBar.setMessage("Endpoint OSNAP ON");
            }
            case OSNAP_OFF -> {
                setEndpointSnap(false);
                statusBar.setMessage("Endpoint OSNAP OFF");
            }
            case LENGTH -> {
                if (workspace.getCanvas().hasWallStart()) {
                    boolean ok = workspace.getCanvas().completeWallWithLength(r.valueMm());
                    if (ok) {
                        onModelChanged();
                    }
                } else {
                    statusBar.setMessage(String.format(
                            "Length %.0f mm — start WALL/LINE first, then re-enter length",
                            r.valueMm()
                    ));
                    setTool(DrawTool.WALL);
                }
            }
            case UNKNOWN -> statusBar.setMessage(r.message());
        }
    }

    public void focusCommandLine() {
        statusBar.focusCommandLine();
    }

    public void calibrateBackgroundScale() {
        if (project == null || project.floorPlan().background() == null) {
            statusBar.setMessage("Import a floor plan background first (Import plan…)");
            return;
        }
        setTool(DrawTool.CALIBRATE_SCALE);
        statusBar.setMessage("Scale: click two points of a known length on the background");
    }

    public void showPriceBook() {
        PriceBookDialog.show(stage);
        boqPanel.refresh();
        symbolLibraryPanel.reload();
    }

    public void toggleTelemetryOptIn() {
        boolean next = !userPrefs.isTelemetryOptIn();
        userPrefs.setTelemetryOptIn(next);
        TelemetryService.get().setEnabled(next);
        statusBar.setMessage(next
                ? "Telemetry opt-in ON — only generic events (no floor plans)"
                : "Telemetry OFF");
    }

    public void newFromTemplateOneBed() {
        newFromTemplate(SampleProjectFactory::createOneBedBungalow, SampleProjectFactory.ONE_BED_NAME);
    }

    public void newFromTemplateThreeBed() {
        newFromTemplate(SampleProjectFactory::createThreeBedBungalow, SampleProjectFactory.SAMPLE_NAME);
    }

    public void newFromTemplateTwoStorey() {
        newFromTemplate(SampleProjectFactory::createTwoStoreyHouse, SampleProjectFactory.TWO_STOREY_NAME);
    }

    private void newFromTemplate(Supplier<Project> factory, String label) {
        if (!promptUnsavedChanges()) {
            return;
        }
        try {
            Project loaded = factory.get();
            applyProject(loaded, null, false, "New from template: " + label
                    + " · " + loaded.totalRoomCount() + " room(s), "
                    + loaded.totalDeviceCount() + " device(s)");
        } catch (Exception ex) {
            log.error("Template open failed", ex);
            ErrorDialog.show(stage, "Could not create template project", ex);
        }
    }

    private void applyProject(Project loaded, Path path, boolean isDirty, String status) {
        project = loaded;
        projectPath = path;
        dirty = isDirty;
        resetCalcSession();
        workspace.bindProject(project);
        propertiesPanel.setProject(project);
        boqPanel.setProject(project);
        electricalPanel.setProject(project);
        calcResultsPanel.clear();
        workspace.getCanvas().clearAiPreview();
        workspace.getCanvas().fitToWindow();
        refreshTitleAndStatus();
        refreshSelection();
        statusBar.setMessage(status);
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

    public void setShowArchitectureLayer(boolean show) {
        workspace.getCanvas().getCadSettings().setShowArchitecture(show);
        workspace.getCanvas().redraw();
    }

    public void setShowElectricalLayer(boolean show) {
        workspace.getCanvas().getCadSettings().setShowElectrical(show);
        workspace.getCanvas().redraw();
    }

    public void setOrthoMode(boolean on) {
        workspace.getCanvas().getCadSettings().setOrtho(on);
    }

    public void setEndpointSnap(boolean on) {
        workspace.getCanvas().getCadSettings().setEndpointSnap(on);
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

    /**
     * Loads the bundled sample three-bedroom Ghana bungalow project.
     */
    public void openSampleThreeBedHouse() {
        if (!promptUnsavedChanges()) {
            return;
        }
        try {
            Project loaded;
            try (InputStream in = getClass().getResourceAsStream(SampleProjectFactory.RESOURCE_PATH)) {
                if (in != null) {
                    Path tmp = Files.createTempFile("gwire-sample-", ".gwire");
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    loaded = projectStore.load(tmp);
                    Files.deleteIfExists(tmp);
                } else {
                    loaded = SampleProjectFactory.createThreeBedBungalow();
                }
            }
            project = loaded;
            projectPath = null;
            dirty = false;
            resetCalcSession();
            workspace.bindProject(project);
            propertiesPanel.setProject(project);
            boqPanel.setProject(project);
            electricalPanel.setProject(project);
            calcResultsPanel.clear();
            workspace.getCanvas().fitToWindow();
            refreshTitleAndStatus();
            refreshSelection();
            statusBar.setMessage("Opened sample: " + project.name()
                    + " · " + project.floorPlan().rooms().size() + " rooms, "
                    + project.floorPlan().devices().size() + " devices");
        } catch (Exception ex) {
            log.error("Sample open failed", ex);
            try {
                project = SampleProjectFactory.createThreeBedBungalow();
                projectPath = null;
                dirty = false;
                resetCalcSession();
                workspace.bindProject(project);
                propertiesPanel.setProject(project);
                boqPanel.setProject(project);
                electricalPanel.setProject(project);
                calcResultsPanel.clear();
                workspace.getCanvas().fitToWindow();
                refreshTitleAndStatus();
                refreshSelection();
                statusBar.setMessage("Opened sample (in-memory): " + project.name());
            } catch (Exception ex2) {
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.initOwner(stage);
                err.setTitle("Sample project");
                err.setHeaderText("Could not open sample");
                err.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                err.showAndWait();
            }
        }
    }

    public void showAbout() {
        int n = LibraryBootstrap.get() == null ? 0 : LibraryBootstrap.get().count();
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.initOwner(stage);
        about.setTitle("About " + GWireApp.APP_NAME);
        about.setHeaderText(GWireApp.APP_NAME + " (" + GWireApp.APP_SHORT_NAME + ")");
        about.setContentText(
                """
                Version: %s (beta)

                %s

                AI-assisted electrical wiring design for Ghana households.
                Preliminary design aid only — a CEWP must verify real installations.

                Stack: Java 21+, JavaFX 23+, Maven, H2, PDFBox, Apache POI
                Licence: see LICENSE in the project repository.
                Build: %s

                Catalogue: %d items · AI: %s
                Docs: docs/USER_GUIDE.md · Help → Open Sample 3-Bed House
                Releases: https://github.com/drapiigi/EDesignTool/releases
                """.formatted(
                        GWireApp.APP_VERSION,
                        GWireApp.standardsStamp(),
                        userPrefs.isBuildSigned() ? "signed" : "unsigned beta",
                        n,
                        AiSettings.load().isLlmAvailable() ? "LLM ready" : "rules only (no API key)"
                )
        );
        about.showAndWait();
    }

    public void showUserGuide() {
        Alert guide = new Alert(Alert.AlertType.INFORMATION);
        guide.initOwner(stage);
        guide.setTitle("Quick start");
        guide.setHeaderText(GWireApp.APP_NAME + " — quick start");
        guide.setContentText(
                """
                1. File → New from Template (1-bed / 3-bed / 2-storey) or Help → Sample
                2. Draw walls/rooms or import a plan · Tools → Calibrate scale if needed
                3. Drag symbols from the library onto the plan
                4. Tools → Recalculate Loads · review Electrical model panel
                5. Design → AI Generate (ghost preview: Accept selected / Reject)
                6. File → Export PDF Report or Export BOQ (Excel)

                CAD: type LINE or a length (3500 / 3.5m) in the Cmd: field
                Price book: Tools → Price book…

                Full notes: docs/USER_GUIDE.md · shortcuts: Help → Keyboard shortcuts
                Disclaimer: design aid only — CEWP verification required.
                """
        );
        guide.showAndWait();
    }

    public void showKeyboardCheatSheet() {
        Alert keys = new Alert(Alert.AlertType.INFORMATION);
        keys.initOwner(stage);
        keys.setTitle("Keyboard shortcuts");
        keys.setHeaderText("GhanaWire CAD & app shortcuts");
        keys.setContentText(
                """
                File
                  Ctrl+N  New project          Ctrl+O  Open
                  Ctrl+S  Save                 Ctrl+E  Export PDF
                  Ctrl+I  Import floor plan    Ctrl+Q  Exit

                Edit / view
                  Ctrl+Z  Undo                 Ctrl+Y  Redo
                  Delete  Delete selection
                  Ctrl+=  Zoom in              Ctrl+-  Zoom out
                  Ctrl+0  Fit to window

                Design / tools
                  Ctrl+G  AI generate (preview)
                  Ctrl+R  Recalculate loads
                  Ctrl+L  Validate standards
                  Ctrl+;  Focus CAD command line

                Canvas
                  F8      Ortho on/off
                  F3      Endpoint OSNAP
                  Shift   Temporary ortho (wall)
                  Space   Temporary pan
                  Esc     Cancel in-progress draw

                CAD command line (bottom)
                  LINE / L     start wall tool
                  3500 / 3.5m  complete wall length
                  ORTHO ON|OFF · OSNAP ON|OFF · HELP

                See docs/KEYBOARD.md for the full list.
                """
        );
        keys.showAndWait();
    }

    public void quit() {
        // Triggers onCloseRequest (Save / Don't save / Cancel)
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        if (!stage.isShowing()) {
            // already closed
            return;
        }
        // If still showing, user cancelled — do nothing
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
                + " · " + project.activeStorey().displayLabel()
                + " · storeys " + project.storeys().size()
                + " · rooms " + project.totalRoomCount()
                + " · devices " + project.totalDeviceCount()
                + " · routes " + project.floorPlan().wiringRoutes().size();
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
