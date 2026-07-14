package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.ai.AiPreviewSession;
import com.ghana.gwire.ai.DesignPlacement;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.LinearDimension;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.floorplan.WiringRoute;
import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.service.history.FloorPlanHistory;
import com.ghana.gwire.ui.symbols.ComponentDragFormats;
import com.ghana.gwire.ui.symbols.SymbolRenderer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ZoomEvent;
import javafx.animation.AnimationTimer;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Floor plan canvas: grid, pan/zoom, walls/rooms/openings, drag-drop place & move devices.
 * World units = millimetres. Screen mapping via scale (px per mm) and pan offsets.
 */
public class FloorPlanCanvas {

    private final Pane root;
    private final Canvas canvas;
    private final ObjectProperty<DrawTool> tool = new SimpleObjectProperty<>(DrawTool.SELECT);
    private final SelectionModel selection = new SelectionModel();
    private final FloorPlanHistory history;
    private final CadSettings cadSettings = new CadSettings();
    private final Map<String, Image> rasterCache = new HashMap<>();

    private FloorPlan floorPlan = new FloorPlan();
    private Project project;
    private String activeStoreyId;
    private Consumer<String> statusSink = s -> {
    };
    private Runnable selectionListener = () -> {
    };
    private Runnable modelChangeListener = () -> {
    };
    /** Endpoint OSNAP hover indicator (world mm). */
    private Vec2 snapMarker;
    /** Shift held → temporary ortho for wall tool. */
    private boolean shiftOrtho;

    /**
     * Pixels per millimetre (view zoom).
     * ~0.06 ≈ fit a 12 m house in a typical window at ~1:100 print feel.
     */
    private double scale = 0.06;
    private double panX = 48;
    private double panY = 48;

    private Vec2 wallStart;
    /** First point of two-point scale calibration. */
    private Vec2 calibrateStart;
    /** First point of linear dimension tool. */
    private Vec2 dimStart;
    private Vec2 dragStartWorld;
    private Vec2 roomOrigin;
    private boolean panning;
    /** Active AI ghost preview (non-destructive). */
    private AiPreviewSession aiPreview;
    private Consumer<AiPreviewSession> aiPreviewClickListener = s -> {
    };

    /** Phase 13b grip stretch */
    private enum GripTarget {
        WALL_START, WALL_END, ROOM_TL, ROOM_TR, ROOM_BR, ROOM_BL
    }

    private GripTarget activeGrip;
    private Wall gripWall;
    private Room gripRoom;
    private boolean gripHistoryPushed;
    private double panAnchorX;
    private double panAnchorY;
    private double panOriginX;
    private double panOriginY;

    // Rubber-band previews
    private Vec2 previewEnd;
    private Vec2 roomPreviewCorner;

    /** Catalogue component pending placement (PLACE_DEVICE tool fallback). */
    private ElectricalComponent pendingComponent;

    /** Live drag-move of a placed device (SELECT tool only, after drag threshold). */
    private PlacedDevice movingDevice;
    /** Device under cursor on press; becomes {@link #movingDevice} only after drag threshold. */
    private PlacedDevice moveCandidate;
    private boolean moveHistoryRecorded;
    private double moveGrabOffsetX;
    private double moveGrabOffsetY;
    private double pressScreenX;
    private double pressScreenY;
    private boolean dropHighlight;
    /** Space held → temporary pan (CAD-style hand tool). */
    private boolean spacePan;
    /** Pixel drag distance before a SELECT press becomes a device move (vs click-select). */
    private static final double MOVE_DRAG_THRESHOLD_PX = 5;

    /** Coalesce multiple redraw() calls into one paint per pulse. */
    private boolean redrawPending;
    private final AnimationTimer redrawTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (redrawPending) {
                redrawPending = false;
                paintNow();
            }
            stop();
        }
    };

    public FloorPlanCanvas(FloorPlanHistory history) {
        this.history = Objects.requireNonNull(history);
        canvas = new Canvas(800, 600);
        root = new Pane(canvas);
        root.getStyleClass().add("floor-plan-canvas-host");
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> redraw());
        canvas.heightProperty().addListener((o, a, b) -> redraw());

        cadSettings.orthoProperty().addListener((o, a, b) -> {
            status("Ortho " + (b ? "ON (F8)" : "OFF (F8)"));
            redraw();
        });
        cadSettings.endpointSnapProperty().addListener((o, a, b) -> {
            status("Endpoint OSNAP " + (b ? "ON" : "OFF"));
            redraw();
        });
        cadSettings.showArchitectureProperty().addListener((o, a, b) -> redraw());
        cadSettings.showElectricalProperty().addListener((o, a, b) -> redraw());

        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPress);
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDrag);
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onRelease);
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMove);
        // Filters so touchpad scroll/pinch are handled before parents steal them
        canvas.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        canvas.addEventFilter(ScrollEvent.SCROLL_STARTED, this::onScrollLifecycle);
        canvas.addEventFilter(ScrollEvent.SCROLL_FINISHED, this::onScrollLifecycle);
        canvas.addEventFilter(ZoomEvent.ANY, this::onZoom);
        root.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        root.addEventFilter(ZoomEvent.ANY, this::onZoom);

        // Drag-and-drop from symbol library
        canvas.setOnDragOver(this::onExternalDragOver);
        canvas.setOnDragEntered(this::onExternalDragEntered);
        canvas.setOnDragExited(this::onExternalDragExited);
        canvas.setOnDragDropped(this::onExternalDragDropped);

        root.setFocusTraversable(true);
        canvas.setFocusTraversable(true);
        root.addEventHandler(KeyEvent.KEY_PRESSED, this::onKey);
        root.addEventHandler(KeyEvent.KEY_RELEASED, this::onKeyReleased);
        canvas.addEventHandler(KeyEvent.KEY_PRESSED, this::onKey);
        canvas.addEventHandler(KeyEvent.KEY_RELEASED, this::onKeyReleased);
        root.setOnMouseClicked(e -> {
            root.requestFocus();
            canvas.requestFocus();
        });
        canvas.setOnMouseEntered(e -> canvas.requestFocus());

        tool.addListener((o, a, b) -> {
            if (a == b) {
                return;
            }
            cancelInProgress();
            updateCursorForTool();
            status("Tool: " + b.label());
            redraw();
        });
        updateCursorForTool();
    }

    public Pane getRoot() {
        return root;
    }

    public ObjectProperty<DrawTool> toolProperty() {
        return tool;
    }

    public DrawTool getTool() {
        return tool.get();
    }

    public void setTool(DrawTool t) {
        if (t == null || t == tool.get()) {
            return;
        }
        tool.set(t);
    }

    /**
     * Arms place mode for a catalogue component (fallback if drag-and-drop is not used).
     * Prefer dragging from the symbol library onto the canvas.
     */
    public void beginPlaceComponent(ElectricalComponent component) {
        this.pendingComponent = Objects.requireNonNull(component, "component");
        setTool(DrawTool.PLACE_DEVICE);
        status("Drag from library preferred — or click canvas to place: " + component.name());
    }

    /**
     * Places a catalogue component at world coordinates (used by drag-drop and place tool).
     */
    public PlacedDevice placeComponentAt(ElectricalComponent component, Vec2 world) {
        Objects.requireNonNull(component, "component");
        Vec2 pos = constrainPoint(Objects.requireNonNull(world, "world"), null);
        pushHistory();
        PlacedDevice device = new PlacedDevice(component.id(), component.symbolKey(), pos.x(), pos.y());
        device.setNameOverride(component.name());
        floorPlan.hitRoom(pos).ifPresent(r -> device.setRoomId(r.id()));
        floorPlan.addDevice(device);
        selection.selectDevice(device);
        fireSelection();
        fireModelChanged();
        status("Placed " + component.name() + " at (%.0f, %.0f) mm".formatted(pos.x(), pos.y()));
        redraw();
        return device;
    }

    public ElectricalComponent getPendingComponent() {
        return pendingComponent;
    }

    public void clearPendingComponent() {
        pendingComponent = null;
    }

    public SelectionModel getSelection() {
        return selection;
    }

    public CadSettings getCadSettings() {
        return cadSettings;
    }

    public void bindProject(Project project, String storeyId) {
        this.project = project;
        this.activeStoreyId = storeyId;
    }

    public void setActiveStoreyId(String storeyId) {
        this.activeStoreyId = storeyId;
    }

    private void pushHistory() {
        history.push(activeStoreyId, floorPlan);
    }

    public FloorPlan getFloorPlan() {
        return floorPlan;
    }

    public void setFloorPlan(FloorPlan floorPlan) {
        this.floorPlan = Objects.requireNonNull(floorPlan);
        selection.clear();
        cancelInProgress();
        history.clear();
        redraw();
        fireSelection();
    }

    public void setStatusSink(Consumer<String> statusSink) {
        this.statusSink = statusSink == null ? s -> {
        } : statusSink;
    }

    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener == null ? () -> {
        } : selectionListener;
    }

    public void setModelChangeListener(Runnable modelChangeListener) {
        this.modelChangeListener = modelChangeListener == null ? () -> {
        } : modelChangeListener;
    }

    private void fireModelChanged() {
        modelChangeListener.run();
    }

    public void registerRaster(String path, Image image) {
        if (path != null && image != null) {
            rasterCache.put(path, image);
        }
    }

    public void clearRasterCache() {
        rasterCache.clear();
    }

    public void zoomIn() {
        zoomAt(canvas.getWidth() / 2, canvas.getHeight() / 2, 1.15);
    }

    public void zoomOut() {
        zoomAt(canvas.getWidth() / 2, canvas.getHeight() / 2, 1 / 1.15);
    }

    public void fitToWindow() {
        double minX = 0;
        double minY = 0;
        double maxX = 12_000;
        double maxY = 10_000;
        boolean any = false;
        for (Wall w : floorPlan.walls()) {
            minX = any ? Math.min(minX, Math.min(w.start().x(), w.end().x())) : Math.min(w.start().x(), w.end().x());
            minY = any ? Math.min(minY, Math.min(w.start().y(), w.end().y())) : Math.min(w.start().y(), w.end().y());
            maxX = any ? Math.max(maxX, Math.max(w.start().x(), w.end().x())) : Math.max(w.start().x(), w.end().x());
            maxY = any ? Math.max(maxY, Math.max(w.start().y(), w.end().y())) : Math.max(w.start().y(), w.end().y());
            any = true;
        }
        for (Room r : floorPlan.rooms()) {
            minX = any ? Math.min(minX, r.x()) : r.x();
            minY = any ? Math.min(minY, r.y()) : r.y();
            maxX = any ? Math.max(maxX, r.x() + r.widthMm()) : r.x() + r.widthMm();
            maxY = any ? Math.max(maxY, r.y() + r.heightMm()) : r.y() + r.heightMm();
            any = true;
        }
        for (PlacedDevice d : floorPlan.devices()) {
            minX = any ? Math.min(minX, d.xMm()) : d.xMm();
            minY = any ? Math.min(minY, d.yMm()) : d.yMm();
            maxX = any ? Math.max(maxX, d.xMm()) : d.xMm();
            maxY = any ? Math.max(maxY, d.yMm()) : d.yMm();
            any = true;
        }
        BackgroundImage bg = floorPlan.background();
        if (bg != null) {
            Image img = rasterCache.get(bg.sourcePath());
            if (img != null) {
                double w = img.getWidth() * bg.mmPerPixel();
                double h = img.getHeight() * bg.mmPerPixel();
                minX = any ? Math.min(minX, bg.originXMm()) : bg.originXMm();
                minY = any ? Math.min(minY, bg.originYMm()) : bg.originYMm();
                maxX = any ? Math.max(maxX, bg.originXMm() + w) : bg.originXMm() + w;
                maxY = any ? Math.max(maxY, bg.originYMm() + h) : bg.originYMm() + h;
                any = true;
            }
        }
        // Margin so walls/symbols are not clipped at edges (AutoCAD zoom extents feel)
        double marginMm = 800;
        minX -= marginMm;
        minY -= marginMm;
        maxX += marginMm;
        maxY += marginMm;
        double worldW = Math.max(2000, maxX - minX);
        double worldH = Math.max(2000, maxY - minY);
        double pad = Math.max(28, Math.min(canvas.getWidth(), canvas.getHeight()) * 0.04);
        double sx = (canvas.getWidth() - 2 * pad) / worldW;
        double sy = (canvas.getHeight() - 2 * pad) / worldH;
        scale = Math.clamp(Math.min(sx, sy), 0.004, 0.45);
        // Center the extents in the viewport
        double contentW = worldW * scale;
        double contentH = worldH * scale;
        panX = (canvas.getWidth() - contentW) / 2.0 - minX * scale;
        panY = (canvas.getHeight() - contentH) / 2.0 - minY * scale;
        redraw();
        status("Zoom extents · 1:%d · %.3f px/mm".formatted(CadStyle.approxDrawingScale(scale), scale));
    }

    public void undo() {
        history.undo(project, floorPlan);
        selection.clear();
        cancelInProgress();
        redraw();
        fireSelection();
        fireModelChanged();
        status("Undo");
    }

    public void redo() {
        history.redo(project, floorPlan);
        selection.clear();
        cancelInProgress();
        redraw();
        fireSelection();
        fireModelChanged();
        status("Redo");
    }

    public void deleteSelection() {
        if (selection.isEmpty()) {
            return;
        }
        pushHistory();
        switch (selection.kind()) {
            case WALL -> floorPlan.removeWallById(selection.wall().id());
            case ROOM -> floorPlan.removeRoomById(selection.room().id());
            case OPENING -> floorPlan.removeOpeningById(selection.opening().id());
            case DEVICE -> floorPlan.removeDeviceById(selection.device().id());
            case NONE -> {
            }
        }
        selection.clear();
        redraw();
        fireSelection();
        fireModelChanged();
        status("Deleted selection");
    }

    /**
     * Request a repaint. Multiple calls in the same event loop coalesce to one paint
     * (Phase 10 performance).
     */
    public void redraw() {
        redrawPending = true;
        redrawTimer.start();
    }

    /** Immediate paint (used by tests / after coalesce). */
    public void paintNow() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setImageSmoothing(true);
        g.setFill(CadStyle.PAPER);
        g.fillRect(0, 0, w, h);

        drawGrid(g, w, h);
        drawBackground(g);
        if (cadSettings.isShowArchitecture()) {
            drawRooms(g);
            drawWalls(g);
            drawOpenings(g);
            drawDimensions(g);
            drawGrips(g);
        }
        if (cadSettings.isShowElectrical()) {
            drawWiringRoutes(g);
            drawDevices(g);
            drawAiGhosts(g);
        }
        drawPreview(g);
        drawCalibratePreview(g);
        drawSnapMarker(g);
        if (dropHighlight) {
            CadStyle.applyCadStroke(g, 2.5);
            g.setStroke(CadStyle.ACCENT);
            g.setLineDashes(10, 6);
            g.strokeRect(5, 5, w - 10, h - 10);
            g.setLineDashes(null);
            g.setFill(Color.web("#88c0d0", 0.07));
            g.fillRect(0, 0, w, h);
        }
        drawHud(g, w, h);
    }

    // --- coordinate transforms ---

    private Vec2 screenToWorld(double sx, double sy) {
        return new Vec2((sx - panX) / scale, (sy - panY) / scale);
    }

    private double worldToScreenX(double x) {
        return x * scale + panX;
    }

    private double worldToScreenY(double y) {
        return y * scale + panY;
    }

    private void zoomAt(double sx, double sy, double factor) {
        Vec2 before = screenToWorld(sx, sy);
        scale = Math.clamp(scale * factor, 0.005, 0.5);
        panX = sx - before.x() * scale;
        panY = sy - before.y() * scale;
        redraw();
    }

    // --- input ---

    private void beginPan(double sx, double sy) {
        panning = true;
        moveCandidate = null;
        movingDevice = null;
        panAnchorX = sx;
        panAnchorY = sy;
        panOriginX = panX;
        panOriginY = panY;
        canvas.setCursor(javafx.scene.Cursor.CLOSED_HAND);
    }

    private void endPan() {
        if (panning) {
            panning = false;
            updateCursorForTool();
        }
    }

    private void updateCursorForTool() {
        if (spacePan || getTool() == DrawTool.PAN) {
            canvas.setCursor(javafx.scene.Cursor.OPEN_HAND);
        } else {
            canvas.setCursor(javafx.scene.Cursor.DEFAULT);
        }
    }

    private void onPress(MouseEvent e) {
        root.requestFocus();
        canvas.requestFocus();
        pressScreenX = e.getX();
        pressScreenY = e.getY();
        shiftOrtho = e.isShiftDown();
        Vec2 raw = screenToWorld(e.getX(), e.getY());
        Vec2 world = constrainPoint(raw, wallStart);

        // View pan: middle mouse, right mouse, Space+drag, or Pan tool — moves whole plan together
        if (e.getButton() == MouseButton.MIDDLE
                || e.getButton() == MouseButton.SECONDARY
                || spacePan
                || getTool() == DrawTool.PAN) {
            beginPan(e.getX(), e.getY());
            e.consume();
            return;
        }

        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        switch (getTool()) {
            case SELECT -> beginSelectOrMove(world, e.getX(), e.getY());
            case WALL -> {
                if (wallStart == null) {
                    wallStart = world;
                    previewEnd = world;
                    status("Wall: click end point · Ortho " + (isOrthoActive() ? "ON" : "OFF")
                            + " · OSNAP " + (cadSettings.isEndpointSnap() ? "ON" : "OFF"));
                } else {
                    commitWall(wallStart, world);
                    wallStart = null;
                    previewEnd = null;
                    snapMarker = null;
                }
            }
            case ROOM -> {
                roomOrigin = world;
                roomPreviewCorner = world;
                dragStartWorld = world;
            }
            case DOOR -> placeOpening(world, OpeningType.DOOR, 900);
            case WINDOW -> placeOpening(world, OpeningType.WINDOW, 1200);
            case PLACE_DEVICE -> placePendingDevice(world);
            case CALIBRATE_SCALE -> onCalibrateClick(world);
            case DIMENSION -> onDimensionClick(world);
            case PAN -> beginPan(e.getX(), e.getY());
        }
        // Ghost toggle when SELECT + preview active
        if (getTool() == DrawTool.SELECT && aiPreview != null) {
            int hit = hitTestGhost(world);
            if (hit >= 0) {
                aiPreview.toggle(hit);
                aiPreviewClickListener.accept(aiPreview);
                status("Ghost " + (hit + 1) + " "
                        + (aiPreview.isSelected(hit) ? "selected" : "deselected")
                        + " · " + aiPreview.selectedCount() + "/" + aiPreview.size());
            }
        }
        redraw();
    }

    private void onDrag(MouseEvent e) {
        // Promote SELECT device candidate to a real move only after a small drag
        if (!panning && movingDevice == null && moveCandidate != null) {
            double dx = e.getX() - pressScreenX;
            double dy = e.getY() - pressScreenY;
            if (Math.hypot(dx, dy) >= MOVE_DRAG_THRESHOLD_PX) {
                movingDevice = moveCandidate;
                moveCandidate = null;
                moveHistoryRecorded = false;
                status("Moving " + movingDevice.displayName());
            }
        }

        if (panning) {
            panX = panOriginX + (e.getX() - panAnchorX);
            panY = panOriginY + (e.getY() - panAnchorY);
            redraw();
            e.consume();
            return;
        }

        // Grip stretch (wall endpoints / room corners)
        if (activeGrip != null) {
            shiftOrtho = e.isShiftDown();
            Vec2 world = constrainPoint(screenToWorld(e.getX(), e.getY()), null);
            if (!gripHistoryPushed) {
                pushHistory();
                gripHistoryPushed = true;
            }
            applyGrip(world);
            redraw();
            return;
        }

        // Live move placed device (does not change pan — components stay fixed relative to plan)
        if (movingDevice != null) {
            shiftOrtho = e.isShiftDown();
            Vec2 world = constrainPoint(screenToWorld(e.getX(), e.getY()), null);
            double nx = world.x() - moveGrabOffsetX;
            double ny = world.y() - moveGrabOffsetY;
            Vec2 snapped = floorPlan.snap(new Vec2(nx, ny));
            if (!moveHistoryRecorded) {
                pushHistory();
                moveHistoryRecorded = true;
            }
            movingDevice.setPosition(snapped.x(), snapped.y());
            floorPlan.hitRoom(snapped).ifPresentOrElse(
                    r -> movingDevice.setRoomId(r.id()),
                    () -> movingDevice.setRoomId(null)
            );
            selection.selectDevice(movingDevice);
            fireSelection();
            status("Moving %s -> (%.0f, %.0f) mm"
                    .formatted(movingDevice.displayName(), snapped.x(), snapped.y()));
            redraw();
            return;
        }

        shiftOrtho = e.isShiftDown();
        Vec2 world = constrainPoint(screenToWorld(e.getX(), e.getY()), wallStart);
        if (getTool() == DrawTool.WALL && wallStart != null) {
            previewEnd = world;
            redraw();
        } else if (getTool() == DrawTool.CALIBRATE_SCALE && calibrateStart != null) {
            previewEnd = world;
            redraw();
        } else if (getTool() == DrawTool.DIMENSION && dimStart != null) {
            previewEnd = world;
            redraw();
        } else if (getTool() == DrawTool.ROOM && roomOrigin != null) {
            roomPreviewCorner = world;
            redraw();
        }
    }

    private void onRelease(MouseEvent e) {
        if (panning) {
            endPan();
            e.consume();
            return;
        }
        // Click (no drag): keep selection only
        moveCandidate = null;
        if (activeGrip != null) {
            boolean changed = gripHistoryPushed;
            activeGrip = null;
            gripWall = null;
            gripRoom = null;
            gripHistoryPushed = false;
            if (changed) {
                floorPlan.notifyGeometryMutated();
                fireModelChanged();
                status("Grip edit applied");
            }
            fireSelection();
            redraw();
            return;
        }
        if (movingDevice != null) {
            PlacedDevice done = movingDevice;
            boolean moved = moveHistoryRecorded;
            movingDevice = null;
            moveHistoryRecorded = false;
            if (moved) {
                floorPlan.notifyGeometryMutated();
                status("Moved %s to (%.0f, %.0f) mm"
                        .formatted(done.displayName(), done.xMm(), done.yMm()));
                fireModelChanged();
            }
            fireSelection();
            redraw();
            return;
        }
        if (getTool() == DrawTool.ROOM && roomOrigin != null && roomPreviewCorner != null) {
            commitRoom(roomOrigin, roomPreviewCorner);
            roomOrigin = null;
            roomPreviewCorner = null;
            dragStartWorld = null;
            redraw();
        }
    }

    private void onMove(MouseEvent e) {
        shiftOrtho = e.isShiftDown();
        Vec2 raw = screenToWorld(e.getX(), e.getY());
        if (getTool() == DrawTool.WALL) {
            Vec2 world = constrainPoint(raw, wallStart);
            if (wallStart != null) {
                previewEnd = world;
            }
            redraw();
        } else if (getTool() == DrawTool.CALIBRATE_SCALE && calibrateStart != null) {
            previewEnd = constrainPoint(raw, calibrateStart);
            redraw();
        } else if (getTool() == DrawTool.DIMENSION && dimStart != null) {
            previewEnd = constrainPoint(raw, dimStart);
            redraw();
        } else if (cadSettings.isEndpointSnap() && getTool() != DrawTool.PAN) {
            // Update snap marker when hovering near endpoints
            constrainPoint(raw, null);
            redraw();
        }
    }

    /**
     * Touchpad / mouse wheel handling:
     * <ul>
     *   <li>Two-finger scroll (trackpad): pan the plan</li>
     *   <li>Ctrl/⌘/Alt + scroll, or discrete mouse wheel lines: zoom toward cursor</li>
     *   <li>Pinch (ZoomEvent on supporting platforms): zoom</li>
     * </ul>
     */
    private void onScroll(ScrollEvent e) {
        double dx = e.getDeltaX();
        double dy = e.getDeltaY();
        // Some drivers report motion only in totalDelta*
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) {
            dx = e.getTotalDeltaX();
            dy = e.getTotalDeltaY();
        }

        if (shouldZoomFromScroll(e, dx, dy)) {
            if (Math.abs(dy) < 1e-6 && Math.abs(dx) > 1e-6) {
                dy = dx; // side-wheel / horizontal-as-zoom when zooming
            }
            if (Math.abs(dy) < 1e-6) {
                e.consume();
                return;
            }
            // Proportional zoom: small trackpad steps stay smooth; mouse wheel still snappy
            double factor;
            if (Math.abs(dy) < 8) {
                factor = Math.exp(dy * 0.004);
            } else {
                factor = dy > 0 ? 1.12 : 1.0 / 1.12;
            }
            factor = Math.clamp(factor, 0.80, 1.25);
            zoomAt(e.getX(), e.getY(), factor);
            status("Zoom %.3f px/mm · two-finger pan · Ctrl+scroll zoom".formatted(scale));
        } else {
            // Two-finger pan (natural scrolling: finger moves content with deltas)
            panX += dx;
            panY += dy;
            redraw();
        }
        e.consume();
    }

    private void onScrollLifecycle(ScrollEvent e) {
        // Consume started/finished so nested scroll panes do not steal the gesture
        e.consume();
    }

    private void onZoom(ZoomEvent e) {
        // Pinch-to-zoom (when the platform delivers ZoomEvent)
        double factor = e.getZoomFactor();
        if (!Double.isFinite(factor) || factor <= 0) {
            e.consume();
            return;
        }
        factor = Math.clamp(factor, 0.80, 1.25);
        zoomAt(e.getX(), e.getY(), factor);
        status("Zoom %.3f px/mm".formatted(scale));
        e.consume();
    }

    /**
     * Decide pan vs zoom for a scroll event.
     * Trackpads emit pixel deltas often with both axes; mouse wheels use LINE units.
     */
    private static boolean shouldZoomFromScroll(ScrollEvent e, double dx, double dy) {
        if (e.isControlDown() || e.isShortcutDown() || e.isAltDown() || e.isMetaDown()) {
            return true;
        }
        // Classic mouse wheel: vertical lines, little/no horizontal component
        if (e.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.LINES
                && Math.abs(dx) < 0.5) {
            return true;
        }
        // Some systems report mouse wheel as NONE with larger discrete deltas
        if (e.getTextDeltaYUnits() == ScrollEvent.VerticalTextScrollUnits.NONE
                && Math.abs(dx) < 0.5
                && Math.abs(dy) >= 20
                && !e.isInertia()) {
            return true;
        }
        // Default: two-finger trackpad scroll pans
        return false;
    }

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.SHIFT) {
            shiftOrtho = true;
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F8) {
            cadSettings.toggleOrtho();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F3) {
            cadSettings.setEndpointSnap(!cadSettings.isEndpointSnap());
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown()) {
            if (!spacePan) {
                spacePan = true;
                updateCursorForTool();
                status("Space+drag to pan the whole plan");
            }
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            pendingComponent = null;
            if (getTool() == DrawTool.PLACE_DEVICE) {
                setTool(DrawTool.SELECT);
            }
            cancelInProgress();
            selection.clear();
            fireSelection();
            redraw();
            status("Cancelled");
            e.consume();
        } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            deleteSelection();
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.Z) {
            if (e.isShiftDown()) {
                redo();
            } else {
                undo();
            }
            e.consume();
        } else if (e.isControlDown() && e.getCode() == KeyCode.Y) {
            redo();
            e.consume();
        }
    }

    private void onKeyReleased(KeyEvent e) {
        if (e.getCode() == KeyCode.SHIFT) {
            shiftOrtho = false;
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.SPACE) {
            spacePan = false;
            if (!panning) {
                updateCursorForTool();
            }
            e.consume();
        }
    }

    private boolean isOrthoActive() {
        return cadSettings.isOrtho() || shiftOrtho;
    }

    /**
     * Grid snap + optional endpoint OSNAP + optional ortho constraint relative to {@code anchor}.
     */
    private Vec2 constrainPoint(Vec2 world, Vec2 anchor) {
        Vec2 p = floorPlan.snap(world);
        snapMarker = null;
        if (cadSettings.isEndpointSnap()) {
            double tolMm = pickTolMm(12);
            Vec2 ep = nearestEndpoint(p, tolMm);
            if (ep != null) {
                p = ep;
                snapMarker = ep;
            }
        }
        if (anchor != null && isOrthoActive()) {
            double dx = Math.abs(p.x() - anchor.x());
            double dy = Math.abs(p.y() - anchor.y());
            if (dx >= dy) {
                p = new Vec2(p.x(), anchor.y());
            } else {
                p = new Vec2(anchor.x(), p.y());
            }
            p = floorPlan.snap(p);
            // re-apply endpoint snap after ortho if still close
            if (cadSettings.isEndpointSnap()) {
                Vec2 ep = nearestEndpoint(p, pickTolMm(10));
                if (ep != null) {
                    // Prefer ortho wall if endpoint is almost on the ortho line
                    if (Math.abs(ep.y() - anchor.y()) < pickTolMm(6)
                            || Math.abs(ep.x() - anchor.x()) < pickTolMm(6)) {
                        if (Math.abs(ep.y() - anchor.y()) <= Math.abs(ep.x() - anchor.x())) {
                            p = new Vec2(ep.x(), anchor.y());
                        } else {
                            p = new Vec2(anchor.x(), ep.y());
                        }
                        snapMarker = ep;
                    }
                }
            }
        }
        return p;
    }

    private Vec2 nearestEndpoint(Vec2 world, double tolMm) {
        Vec2 best = null;
        double bestD = tolMm;
        for (Wall w : floorPlan.walls()) {
            for (Vec2 ep : new Vec2[]{w.start(), w.end()}) {
                double d = world.distanceTo(ep);
                if (d <= bestD) {
                    bestD = d;
                    best = ep;
                }
            }
        }
        return best;
    }

    private void drawSnapMarker(GraphicsContext g) {
        if (snapMarker == null || !cadSettings.isEndpointSnap()) {
            return;
        }
        double sx = worldToScreenX(snapMarker.x());
        double sy = worldToScreenY(snapMarker.y());
        CadStyle.applySharpStroke(g, 1.4);
        g.setStroke(CadStyle.ACCENT);
        double r = 6;
        g.strokeRect(sx - r, sy - r, r * 2, r * 2);
        g.strokeLine(sx - r - 2, sy, sx + r + 2, sy);
        g.strokeLine(sx, sy - r - 2, sx, sy + r + 2);
    }

    /**
     * Screen-pixel pick tolerance converted to plan millimetres.
     * Keeps hit targets roughly constant on screen as zoom changes.
     */
    private double pickTolMm(double screenPx) {
        return screenPx / Math.max(scale, 0.005);
    }

    private double devicePickTolMm() {
        // Slightly larger than half the drawn symbol so targets stay easy to grab
        return pickTolMm(SymbolRenderer.screenSize(scale) * 0.55);
    }

    /**
     * SELECT tool: hit a device to select (drag past threshold to move it);
     * otherwise select geometry or pan the whole drawing together.
     */
    private void beginSelectOrMove(Vec2 world, double sx, double sy) {
        movingDevice = null;
        moveCandidate = null;
        activeGrip = null;
        gripWall = null;
        gripRoom = null;
        gripHistoryPushed = false;

        // Prefer grip handles on current selection
        if (tryBeginGrip(world)) {
            return;
        }

        var device = floorPlan.hitDevice(world, devicePickTolMm());
        if (device.isPresent()) {
            PlacedDevice d = device.get();
            selection.selectDevice(d);
            fireSelection();
            // Do not move until the pointer actually drags — click alone only selects
            moveCandidate = d;
            moveHistoryRecorded = false;
            moveGrabOffsetX = world.x() - d.xMm();
            moveGrabOffsetY = world.y() - d.yMm();
            status("Selected " + d.displayName() + " · drag to move · empty drag / Space / middle pans view");
            return;
        }

        selectAt(world);
        // Empty space (or non-device geometry click): drag pans the entire plan
        beginPan(sx, sy);
        status("Pan view · whole plan moves together");
    }

    private boolean tryBeginGrip(Vec2 world) {
        double tol = pickTolMm(8);
        if (selection.kind() == SelectionModel.Kind.WALL && selection.wall() != null) {
            Wall w = selection.wall();
            if (world.distanceTo(w.start()) <= tol) {
                activeGrip = GripTarget.WALL_START;
                gripWall = w;
                status("Grip: wall start — drag to stretch");
                return true;
            }
            if (world.distanceTo(w.end()) <= tol) {
                activeGrip = GripTarget.WALL_END;
                gripWall = w;
                status("Grip: wall end — drag to stretch");
                return true;
            }
        }
        if (selection.kind() == SelectionModel.Kind.ROOM && selection.room() != null) {
            Room r = selection.room();
            Vec2 tl = new Vec2(r.x(), r.y());
            Vec2 tr = new Vec2(r.x() + r.widthMm(), r.y());
            Vec2 br = new Vec2(r.x() + r.widthMm(), r.y() + r.heightMm());
            Vec2 bl = new Vec2(r.x(), r.y() + r.heightMm());
            if (world.distanceTo(tl) <= tol) {
                activeGrip = GripTarget.ROOM_TL;
                gripRoom = r;
                status("Grip: room corner — drag to resize");
                return true;
            }
            if (world.distanceTo(tr) <= tol) {
                activeGrip = GripTarget.ROOM_TR;
                gripRoom = r;
                status("Grip: room corner — drag to resize");
                return true;
            }
            if (world.distanceTo(br) <= tol) {
                activeGrip = GripTarget.ROOM_BR;
                gripRoom = r;
                status("Grip: room corner — drag to resize");
                return true;
            }
            if (world.distanceTo(bl) <= tol) {
                activeGrip = GripTarget.ROOM_BL;
                gripRoom = r;
                status("Grip: room corner — drag to resize");
                return true;
            }
        }
        return false;
    }

    private void applyGrip(Vec2 world) {
        if (activeGrip == null) {
            return;
        }
        switch (activeGrip) {
            case WALL_START -> {
                if (gripWall != null) {
                    gripWall.setStart(world);
                    floorPlan.notifyGeometryMutated();
                }
            }
            case WALL_END -> {
                if (gripWall != null) {
                    gripWall.setEnd(world);
                    floorPlan.notifyGeometryMutated();
                }
            }
            case ROOM_TL -> resizeRoomFromCorner(gripRoom, world, true, true);
            case ROOM_TR -> resizeRoomFromCorner(gripRoom, world, false, true);
            case ROOM_BR -> resizeRoomFromCorner(gripRoom, world, false, false);
            case ROOM_BL -> resizeRoomFromCorner(gripRoom, world, true, false);
        }
    }

    private void resizeRoomFromCorner(Room r, Vec2 world, boolean left, boolean top) {
        if (r == null) {
            return;
        }
        double x1 = left ? world.x() : r.x();
        double y1 = top ? world.y() : r.y();
        double x2 = left ? r.x() + r.widthMm() : world.x();
        double y2 = top ? r.y() + r.heightMm() : world.y();
        double nx = Math.min(x1, x2);
        double ny = Math.min(y1, y2);
        double nw = Math.max(100, Math.abs(x2 - x1));
        double nh = Math.max(100, Math.abs(y2 - y1));
        r.setBounds(nx, ny, nw, nh);
    }

    private void onDimensionClick(Vec2 world) {
        if (dimStart == null) {
            dimStart = world;
            previewEnd = world;
            status("Dimension: click second point");
            return;
        }
        if (dimStart.distanceTo(world) < 50) {
            status("Dimension too short");
            dimStart = null;
            previewEnd = null;
            return;
        }
        pushHistory();
        LinearDimension dim = new LinearDimension(dimStart, world);
        floorPlan.addDimension(dim);
        dimStart = null;
        previewEnd = null;
        fireModelChanged();
        status("Dimension added · " + dim.displayLabel());
        redraw();
    }

    private void selectAt(Vec2 world) {
        double tol = pickTolMm(10);
        double deviceTol = devicePickTolMm();
        var device = floorPlan.hitDevice(world, deviceTol);
        if (device.isPresent()) {
            selection.selectDevice(device.get());
            fireSelection();
            status(selection.summary());
            return;
        }
        var opening = floorPlan.hitOpening(world, tol);
        if (opening.isPresent()) {
            selection.selectOpening(opening.get());
            fireSelection();
            status(selection.summary());
            return;
        }
        var wall = floorPlan.hitWall(world, tol);
        if (wall.isPresent()) {
            selection.selectWall(wall.get());
            fireSelection();
            status(selection.summary());
            return;
        }
        var room = floorPlan.hitRoom(world);
        if (room.isPresent()) {
            selection.selectRoom(room.get());
            fireSelection();
            status(selection.summary());
            return;
        }
        selection.clear();
        fireSelection();
        status("Nothing selected");
    }

    private void placePendingDevice(Vec2 world) {
        if (pendingComponent == null) {
            status("Drag a component from the symbol library onto the canvas");
            setTool(DrawTool.SELECT);
            return;
        }
        placeComponentAt(pendingComponent, world);
        // Stay in place mode for multi-insert; Esc clears
    }

    // --- external drag & drop (from symbol library) ---

    private void onExternalDragOver(DragEvent e) {
        if (hasComponentDrag(e)) {
            e.acceptTransferModes(TransferMode.COPY);
            // Live ghost position optional: update highlight only
        }
        e.consume();
    }

    private void onExternalDragEntered(DragEvent e) {
        if (hasComponentDrag(e)) {
            dropHighlight = true;
            redraw();
        }
        e.consume();
    }

    private void onExternalDragExited(DragEvent e) {
        dropHighlight = false;
        redraw();
        e.consume();
    }

    private void onExternalDragDropped(DragEvent e) {
        dropHighlight = false;
        boolean success = false;
        Optional<ElectricalComponent> component = resolveDraggedComponent(e);
        if (component.isPresent()) {
            Vec2 world = floorPlan.snap(screenToWorld(e.getX(), e.getY()));
            placeComponentAt(component.get(), world);
            setTool(DrawTool.SELECT);
            pendingComponent = null;
            success = true;
            root.requestFocus();
        } else {
            status("Drop failed — unknown component");
        }
        e.setDropCompleted(success);
        e.consume();
        redraw();
    }

    private static boolean hasComponentDrag(DragEvent e) {
        return e.getDragboard().hasContent(ComponentDragFormats.COMPONENT_ID)
                || e.getDragboard().hasString();
    }

    private Optional<ElectricalComponent> resolveDraggedComponent(DragEvent e) {
        String id = null;
        if (e.getDragboard().hasContent(ComponentDragFormats.COMPONENT_ID)) {
            Object raw = e.getDragboard().getContent(ComponentDragFormats.COMPONENT_ID);
            if (raw != null) {
                id = raw.toString();
            }
        }
        if ((id == null || id.isBlank()) && e.getDragboard().hasString()) {
            id = e.getDragboard().getString();
        }
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        ComponentLibraryService lib = LibraryBootstrap.get();
        if (lib == null) {
            try {
                lib = LibraryBootstrap.initialize();
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
        return lib.getById(id.trim());
    }

    private void commitWall(Vec2 a, Vec2 b) {
        if (a.distanceTo(b) < 50) {
            status("Wall too short — ignored");
            return;
        }
        pushHistory();
        Wall wall = new Wall(a, b);
        floorPlan.addWall(wall);
        selection.selectWall(wall);
        fireSelection();
        fireModelChanged();
        status("Wall added · %.0f mm".formatted(wall.lengthMm()));
    }

    private void commitRoom(Vec2 a, Vec2 b) {
        double x = Math.min(a.x(), b.x());
        double y = Math.min(a.y(), b.y());
        double w = Math.abs(a.x() - b.x());
        double h = Math.abs(a.y() - b.y());
        if (w < 500 || h < 500) {
            status("Room too small — drag a larger rectangle");
            return;
        }
        pushHistory();
        int n = floorPlan.rooms().size() + 1;
        Room room = new Room("Room " + n, x, y, w, h);
        floorPlan.addRoom(room);
        selection.selectRoom(room);
        fireSelection();
        fireModelChanged();
        status("Room added · %.2f m²".formatted(room.areaM2()));
    }

    private void placeOpening(Vec2 world, OpeningType type, double widthMm) {
        double tol = 250 / Math.max(scale, 0.01);
        var wallOpt = floorPlan.hitWall(world, tol);
        if (wallOpt.isEmpty()) {
            status("Click near a wall to place a " + type.name().toLowerCase());
            return;
        }
        Wall wall = wallOpt.get();
        double t = Segment2.closestT(world, wall.start(), wall.end());
        pushHistory();
        Opening opening = new Opening(wall.id(), type, t, widthMm);
        floorPlan.addOpening(opening);
        selection.selectOpening(opening);
        fireSelection();
        fireModelChanged();
        status(type + " placed on wall");
    }

    private void cancelInProgress() {
        wallStart = null;
        calibrateStart = null;
        dimStart = null;
        previewEnd = null;
        roomOrigin = null;
        roomPreviewCorner = null;
        panning = false;
        movingDevice = null;
        moveCandidate = null;
        moveHistoryRecorded = false;
        activeGrip = null;
        gripWall = null;
        gripRoom = null;
        gripHistoryPushed = false;
        if (getTool() != DrawTool.PLACE_DEVICE) {
            pendingComponent = null;
        }
        updateCursorForTool();
    }

    // --- Phase 15: scale calibration, length entry, AI ghosts ---

    public boolean hasWallStart() {
        return wallStart != null;
    }

    public Vec2 wallStartPoint() {
        return wallStart;
    }

    public Vec2 wallPreviewEnd() {
        return previewEnd;
    }

    /**
     * Complete an in-progress wall using polar length from start toward current preview
     * (or +X if no preview).
     *
     * @return true if a wall was committed
     */
    public boolean completeWallWithLength(double lengthMm) {
        if (wallStart == null || lengthMm < 50) {
            status("Start a wall first (LINE / Wall tool), then enter length ≥ 50 mm");
            return false;
        }
        Vec2 dir;
        if (previewEnd != null && wallStart.distanceTo(previewEnd) > 1e-3) {
            dir = previewEnd.subtract(wallStart).normalize();
        } else if (isOrthoActive()) {
            dir = new Vec2(1, 0);
        } else {
            dir = new Vec2(1, 0);
        }
        Vec2 end = wallStart.add(dir.scale(lengthMm));
        end = constrainPoint(end, wallStart);
        // Re-apply exact length after constrain if ortho/grid shifted slightly
        if (wallStart.distanceTo(end) > 1e-3) {
            dir = end.subtract(wallStart).normalize();
            end = wallStart.add(dir.scale(lengthMm));
        }
        commitWall(wallStart, end);
        wallStart = null;
        previewEnd = null;
        snapMarker = null;
        redraw();
        return true;
    }

    /** Public wall commit for CAD commands (LINE from two points). */
    public void addWall(Vec2 a, Vec2 b) {
        commitWall(a, b);
        redraw();
    }

    public void beginWallAt(Vec2 start) {
        setTool(DrawTool.WALL);
        wallStart = constrainPoint(start, null);
        previewEnd = wallStart;
        status("Wall: click end point or type length (mm) in command line");
        redraw();
    }

    public void setAiPreview(AiPreviewSession session) {
        this.aiPreview = session;
        redraw();
    }

    public AiPreviewSession getAiPreview() {
        return aiPreview;
    }

    public void clearAiPreview() {
        this.aiPreview = null;
        redraw();
    }

    public void setAiPreviewClickListener(Consumer<AiPreviewSession> listener) {
        this.aiPreviewClickListener = listener == null ? s -> {
        } : listener;
    }

    private void onCalibrateClick(Vec2 world) {
        if (floorPlan.background() == null) {
            status("Import a floor plan background first, then calibrate scale");
            return;
        }
        if (calibrateStart == null) {
            calibrateStart = world;
            previewEnd = world;
            status("Scale: click second point of a known length on the plan");
            return;
        }
        Vec2 end = world;
        double measuredMm = calibrateStart.distanceTo(end);
        if (measuredMm < 1) {
            status("Calibration points too close — try again");
            calibrateStart = null;
            previewEnd = null;
            return;
        }
        TextInputDialog dialog = new TextInputDialog("3000");
        dialog.setTitle("Calibrate background scale");
        dialog.setHeaderText(String.format(Locale.ROOT,
                "Measured segment: %.0f mm at current scale (%.2f mm/px)%nEnter the real-world length of this segment:",
                measuredMm, floorPlan.background().mmPerPixel()));
        dialog.setContentText("Length (mm or e.g. 3.5m):");
        var result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            status("Scale calibration cancelled");
            calibrateStart = null;
            previewEnd = null;
            return;
        }
        Optional<Double> known = com.ghana.gwire.service.cad.CadCommandParser.parseLengthMm(result.get());
        if (known.isEmpty() || known.get() <= 0) {
            status("Invalid length — calibration cancelled");
            calibrateStart = null;
            previewEnd = null;
            return;
        }
        double knownMm = known.get();
        BackgroundImage bg = floorPlan.background();
        double newMpp = bg.mmPerPixel() * (knownMm / measuredMm);
        if (newMpp <= 0 || !Double.isFinite(newMpp)) {
            status("Invalid scale result");
            calibrateStart = null;
            previewEnd = null;
            return;
        }
        pushHistory();
        bg.setMmPerPixel(newMpp);
        fireModelChanged();
        status(String.format(Locale.ROOT, "Background scale set to %.3f mm/px (known %.0f mm)", newMpp, knownMm));
        calibrateStart = null;
        previewEnd = null;
        setTool(DrawTool.SELECT);
    }

    private int hitTestGhost(Vec2 world) {
        if (aiPreview == null) {
            return -1;
        }
        double tol = SymbolRenderer.hitRadiusMm(scale);
        List<DesignPlacement> list = aiPreview.plan().placements();
        int best = -1;
        double bestD = tol;
        for (int i = 0; i < list.size(); i++) {
            DesignPlacement p = list.get(i);
            double d = Math.hypot(p.xMm() - world.x(), p.yMm() - world.y());
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private void drawAiGhosts(GraphicsContext g) {
        if (aiPreview == null || aiPreview.size() == 0) {
            return;
        }
        double size = SymbolRenderer.screenSize(scale);
        double pad = size + 8;
        double viewW = canvas.getWidth();
        double viewH = canvas.getHeight();
        List<DesignPlacement> list = aiPreview.plan().placements();
        g.save();
        for (int i = 0; i < list.size(); i++) {
            DesignPlacement p = list.get(i);
            double sx = worldToScreenX(p.xMm());
            double sy = worldToScreenY(p.yMm());
            if (sx < -pad || sy < -pad || sx > viewW + pad || sy > viewH + pad) {
                continue;
            }
            boolean sel = aiPreview.isSelected(i);
            g.setGlobalAlpha(sel ? 0.55 : 0.22);
            SymbolRenderer.draw(g, p.symbolKey(), sx, sy, size, p.rotationDeg(), sel);
            // Ghost ring
            g.setGlobalAlpha(sel ? 0.85 : 0.4);
            g.setStroke(sel ? Color.web("#00ffcc") : Color.web("#88aacc"));
            g.setLineWidth(1.5);
            g.setLineDashes(4, 3);
            double r = size * 0.55;
            g.strokeOval(sx - r, sy - r, r * 2, r * 2);
            g.setLineDashes(null);
        }
        g.restore();
    }

    private void drawCalibratePreview(GraphicsContext g) {
        if (getTool() != DrawTool.CALIBRATE_SCALE || calibrateStart == null || previewEnd == null) {
            return;
        }
        g.setStroke(Color.web("#ffaa00"));
        g.setLineWidth(2);
        g.setLineDashes(8, 4);
        g.strokeLine(
                worldToScreenX(calibrateStart.x()), worldToScreenY(calibrateStart.y()),
                worldToScreenX(previewEnd.x()), worldToScreenY(previewEnd.y())
        );
        g.setLineDashes(null);
        double len = calibrateStart.distanceTo(previewEnd);
        g.setFill(Color.web("#ffaa00"));
        g.setFont(CadStyle.labelFont(12));
        g.fillText(String.format(Locale.ROOT, "Calibrate: %.0f mm (current scale)", len),
                worldToScreenX(previewEnd.x()) + 10,
                worldToScreenY(previewEnd.y()) - 10);
    }

    private void status(String msg) {
        statusSink.accept(msg);
    }

    private void fireSelection() {
        selectionListener.run();
    }

    // --- AutoCAD-style drawing ---

    private void drawGrid(GraphicsContext g, double w, double h) {
        double grid = floorPlan.gridMm();
        double major = grid * 5; // e.g. 500 mm minor → 2.5 m major
        double minorPx = grid * scale;
        double majorPx = major * scale;
        Vec2 topLeft = screenToWorld(0, 0);
        Vec2 bottomRight = screenToWorld(w, h);

        // Adaptive density: hide minor (then major) when lines would clutter
        boolean drawMinor = minorPx >= CadStyle.MIN_GRID_SPACING_PX;
        boolean drawMajor = majorPx >= CadStyle.MIN_GRID_SPACING_PX * 0.85;
        if (!drawMinor && !drawMajor) {
            // Still draw origin
            drawOriginAxes(g, w, h);
            return;
        }

        double step = drawMinor ? grid : major;
        double x0 = Math.floor(topLeft.x() / step) * step;
        double y0 = Math.floor(topLeft.y() / step) * step;

        CadStyle.applySharpStroke(g, 0.6);
        for (double x = x0; x <= bottomRight.x() + step * 0.5; x += step) {
            boolean isMajor = Math.abs(Math.IEEEremainder(x, major)) < 1e-3;
            if (!drawMinor && !isMajor) {
                continue;
            }
            if (!drawMajor && isMajor) {
                // still draw as minor tone if major spacing too tight
                isMajor = false;
            }
            g.setStroke(isMajor ? CadStyle.GRID_MAJOR : CadStyle.GRID_MINOR);
            g.setLineWidth(isMajor ? 0.9 : 0.55);
            double sx = worldToScreenX(x);
            g.strokeLine(sx, 0, sx, h);
        }
        for (double y = y0; y <= bottomRight.y() + step * 0.5; y += step) {
            boolean isMajor = Math.abs(Math.IEEEremainder(y, major)) < 1e-3;
            if (!drawMinor && !isMajor) {
                continue;
            }
            g.setStroke(isMajor ? CadStyle.GRID_MAJOR : CadStyle.GRID_MINOR);
            g.setLineWidth(isMajor ? 0.9 : 0.55);
            double sy = worldToScreenY(y);
            g.strokeLine(0, sy, w, sy);
        }
        drawOriginAxes(g, w, h);
    }

    private void drawOriginAxes(GraphicsContext g, double w, double h) {
        CadStyle.applySharpStroke(g, 1.0);
        g.setStroke(CadStyle.GRID_ORIGIN);
        double ox = worldToScreenX(0);
        double oy = worldToScreenY(0);
        g.strokeLine(ox, 0, ox, h);
        g.strokeLine(0, oy, w, oy);
        // AutoCAD-style UCS origin marker
        double arm = 10;
        g.setStroke(Color.web("#ff4444"));
        g.strokeLine(ox, oy, ox + arm, oy); // X
        g.setStroke(Color.web("#44ff44"));
        g.strokeLine(ox, oy, ox, oy - arm); // Y (screen-up)
        g.setFill(CadStyle.GRID_ORIGIN);
        g.fillOval(ox - 2.5, oy - 2.5, 5, 5);
    }

    private void drawBackground(GraphicsContext g) {
        BackgroundImage bg = floorPlan.background();
        if (bg == null) {
            return;
        }
        Image img = rasterCache.get(bg.sourcePath());
        if (img == null) {
            return;
        }
        double wMm = img.getWidth() * bg.mmPerPixel();
        double hMm = img.getHeight() * bg.mmPerPixel();
        g.setGlobalAlpha(bg.opacity());
        g.setImageSmoothing(true);
        g.drawImage(img,
                worldToScreenX(bg.originXMm()),
                worldToScreenY(bg.originYMm()),
                wMm * scale,
                hMm * scale);
        g.setGlobalAlpha(1.0);
    }

    private void drawRooms(GraphicsContext g) {
        for (Room r : floorPlan.rooms()) {
            boolean selected = selection.kind() == SelectionModel.Kind.ROOM
                    && selection.room() != null
                    && selection.room().id().equals(r.id());
            double x = worldToScreenX(r.x());
            double y = worldToScreenY(r.y());
            double w = r.widthMm() * scale;
            double h = r.heightMm() * scale;

            // Floor fill only — walls define the boundary (AutoCAD room hatch feel)
            g.setFill(selected ? CadStyle.ROOM_FILL_SEL : CadStyle.ROOM_FILL);
            g.fillRect(x, y, w, h);
            if (selected) {
                CadStyle.applySharpStroke(g, CadStyle.lineWeight(1.2, scale));
                g.setStroke(CadStyle.WALL_SEL);
                g.setLineDashes(4, 3);
                g.strokeRect(x + 1, y + 1, Math.max(0, w - 2), Math.max(0, h - 2));
                g.setLineDashes(null);
            }

            // Centered room name + area (architectural annotation)
            if (w < 36 || h < 28) {
                continue;
            }
            String title = r.name().toUpperCase();
            String meta = String.format("%.1f m²  ·  %.2f×%.2f m",
                    r.areaM2(), r.widthMm() / 1000.0, r.heightMm() / 1000.0);
            g.setTextAlign(TextAlignment.CENTER);
            g.setFont(CadStyle.roomTitleFont(scale));
            g.setFill(selected ? CadStyle.WALL_SEL : CadStyle.ROOM_TEXT);
            double cx = x + w / 2;
            double cy = y + h / 2;
            g.fillText(title, cx, cy - 2);
            g.setFont(CadStyle.roomMetaFont(scale));
            g.setFill(CadStyle.ROOM_DIM);
            g.fillText(meta, cx, cy + Math.max(11, 280 * scale));
        }
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void drawWalls(GraphicsContext g) {
        for (Wall wall : floorPlan.walls()) {
            boolean selected = selection.kind() == SelectionModel.Kind.WALL
                    && selection.wall() != null
                    && selection.wall().id().equals(wall.id());
            drawDoubleLineWall(g, wall, selected);
        }
    }

    /**
     * Architectural double-line wall at true thickness (default 150 mm).
     * Solid fill + crisp outlines — no construction centerline (plot-ready look).
     */
    private void drawDoubleLineWall(GraphicsContext g, Wall wall, boolean selected) {
        Vec2 a = wall.start();
        Vec2 b = wall.end();
        Vec2 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1e-6) {
            return;
        }
        Vec2 n = dir.perpendicular();
        double halfT = CadStyle.wallHalfMm(wall.thicknessMm(), scale);

        Vec2 a1 = a.add(n.scale(halfT));
        Vec2 a2 = a.add(n.scale(-halfT));
        Vec2 b1 = b.add(n.scale(halfT));
        Vec2 b2 = b.add(n.scale(-halfT));

        double[] xs = {
                worldToScreenX(a1.x()), worldToScreenX(b1.x()),
                worldToScreenX(b2.x()), worldToScreenX(a2.x())
        };
        double[] ys = {
                worldToScreenY(a1.y()), worldToScreenY(b1.y()),
                worldToScreenY(b2.y()), worldToScreenY(a2.y())
        };

        g.setFill(selected ? CadStyle.WALL_SEL_FILL : CadStyle.WALL_FILL);
        g.fillPolygon(xs, ys, 4);

        double lw = CadStyle.lineWeight(selected ? 1.4 : 0.95, scale);
        CadStyle.applySharpStroke(g, lw);
        g.setStroke(selected ? CadStyle.WALL_SEL : CadStyle.WALL_OUTLINE);
        g.strokePolygon(xs, ys, 4);

        // Subtle end caps only when selected (edit affordance)
        if (selected) {
            double r = 3.0;
            g.setFill(CadStyle.WALL_SEL);
            g.fillOval(worldToScreenX(a.x()) - r, worldToScreenY(a.y()) - r, r * 2, r * 2);
            g.fillOval(worldToScreenX(b.x()) - r, worldToScreenY(b.y()) - r, r * 2, r * 2);
        }
    }

    private void drawOpenings(GraphicsContext g) {
        for (Opening o : floorPlan.openings()) {
            var wallOpt = floorPlan.findWall(o.wallId());
            if (wallOpt.isEmpty()) {
                continue;
            }
            Wall wall = wallOpt.get();
            Vec2 center = wall.start().lerp(wall.end(), o.t());
            Vec2 dir = wall.end().subtract(wall.start()).normalize();
            if (dir.length() < 1e-9) {
                continue;
            }
            Vec2 perp = dir.perpendicular();
            double half = o.widthMm() / 2.0;
            Vec2 a = center.add(dir.scale(-half));
            Vec2 b = center.add(dir.scale(half));
            double wallHalfT = CadStyle.wallHalfMm(wall.thicknessMm(), scale);

            boolean selected = selection.kind() == SelectionModel.Kind.OPENING
                    && selection.opening() != null
                    && selection.opening().id().equals(o.id());
            boolean door = o.type() == OpeningType.DOOR;

            if (door) {
                drawDoorCad(g, a, b, dir, perp, wallHalfT, o.widthMm(), selected);
            } else {
                drawWindowCad(g, a, b, dir, perp, wallHalfT, selected);
            }
        }
    }

    /** Plan door: clear opening, leaf, 90° swing — true width in plan mm. */
    private void drawDoorCad(
            GraphicsContext g, Vec2 a, Vec2 b, Vec2 dir, Vec2 perp,
            double wallHalfT, double widthMm, boolean selected
    ) {
        Color stroke = selected ? CadStyle.WALL_SEL : CadStyle.DOOR;
        Color swing = selected ? CadStyle.WALL_SEL.deriveColor(0, 1, 1, 0.50) : CadStyle.DOOR_SWING;
        double pad = 2 / Math.max(scale, 0.01); // ~2 px clearance in mm

        Vec2 a1 = a.add(perp.scale(wallHalfT + pad));
        Vec2 a2 = a.add(perp.scale(-(wallHalfT + pad)));
        Vec2 b1 = b.add(perp.scale(wallHalfT + pad));
        Vec2 b2 = b.add(perp.scale(-(wallHalfT + pad)));
        g.setFill(CadStyle.PAPER);
        g.fillPolygon(
                new double[]{worldToScreenX(a1.x()), worldToScreenX(b1.x()),
                        worldToScreenX(b2.x()), worldToScreenX(a2.x())},
                new double[]{worldToScreenY(a1.y()), worldToScreenY(b1.y()),
                        worldToScreenY(b2.y()), worldToScreenY(a2.y())},
                4
        );

        double lw = CadStyle.lineWeight(selected ? 1.5 : 1.1, scale);
        CadStyle.applySharpStroke(g, lw);
        g.setStroke(stroke);
        // Jambs
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(a2.x()), worldToScreenY(a2.y()));
        g.strokeLine(worldToScreenX(b1.x()), worldToScreenY(b1.y()),
                worldToScreenX(b2.x()), worldToScreenY(b2.y()));

        double leafLen = widthMm;
        double rPx = leafLen * scale;
        double ax = worldToScreenX(a.x());
        double ay = worldToScreenY(a.y());

        // Open leaf at 90° (standard architectural plan symbol)
        Vec2 openEnd = a.add(perp.scale(leafLen));
        CadStyle.applySharpStroke(g, lw);
        g.setStroke(stroke);
        g.strokeLine(ax, ay, worldToScreenX(openEnd.x()), worldToScreenY(openEnd.y()));

        // Swing arc
        g.setStroke(swing);
        CadStyle.applyCadStroke(g, Math.max(0.7, lw * 0.75));
        double dirScreen = Math.toDegrees(Math.atan2(
                worldToScreenY(a.y() + dir.y()) - ay,
                worldToScreenX(a.x() + dir.x()) - ax
        ));
        double perpScreen = Math.toDegrees(Math.atan2(
                worldToScreenY(a.y() + perp.y()) - ay,
                worldToScreenX(a.x() + perp.x()) - ax
        ));
        double start = -dirScreen;
        double extent = -(perpScreen - dirScreen);
        while (extent > 180) {
            extent -= 360;
        }
        while (extent < -180) {
            extent += 360;
        }
        if (Math.abs(extent) < 1) {
            extent = -90;
        }
        g.strokeArc(ax - rPx, ay - rPx, rPx * 2, rPx * 2, start, extent, ArcType.OPEN);

        // Hinge
        double hr = Math.max(1.5, 2.2);
        g.setFill(stroke);
        g.fillOval(ax - hr, ay - hr, hr * 2, hr * 2);
    }

    /** Plan window: frame + double glazing at wall thickness. */
    private void drawWindowCad(
            GraphicsContext g, Vec2 a, Vec2 b, Vec2 dir, Vec2 perp,
            double wallHalfT, boolean selected
    ) {
        Color stroke = selected ? CadStyle.WALL_SEL : CadStyle.WINDOW;
        double pad = 1 / Math.max(scale, 0.01);

        Vec2 a1 = a.add(perp.scale(wallHalfT + pad));
        Vec2 a2 = a.add(perp.scale(-(wallHalfT + pad)));
        Vec2 b1 = b.add(perp.scale(wallHalfT + pad));
        Vec2 b2 = b.add(perp.scale(-(wallHalfT + pad)));
        g.setFill(CadStyle.PAPER);
        g.fillPolygon(
                new double[]{worldToScreenX(a1.x()), worldToScreenX(b1.x()),
                        worldToScreenX(b2.x()), worldToScreenX(a2.x())},
                new double[]{worldToScreenY(a1.y()), worldToScreenY(b1.y()),
                        worldToScreenY(b2.y()), worldToScreenY(a2.y())},
                4
        );

        double lw = CadStyle.lineWeight(selected ? 1.4 : 1.0, scale);
        CadStyle.applySharpStroke(g, lw);
        g.setStroke(stroke);
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(b1.x()), worldToScreenY(b1.y()));
        g.strokeLine(worldToScreenX(a2.x()), worldToScreenY(a2.y()),
                worldToScreenX(b2.x()), worldToScreenY(b2.y()));
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(a2.x()), worldToScreenY(a2.y()));
        g.strokeLine(worldToScreenX(b1.x()), worldToScreenY(b1.y()),
                worldToScreenX(b2.x()), worldToScreenY(b2.y()));

        // Double glazing
        double inset = wallHalfT * 0.38;
        Vec2 g1a = a.add(perp.scale(inset));
        Vec2 g1b = b.add(perp.scale(inset));
        Vec2 g2a = a.add(perp.scale(-inset));
        Vec2 g2b = b.add(perp.scale(-inset));
        g.setFill(CadStyle.WINDOW_GLASS);
        g.fillPolygon(
                new double[]{worldToScreenX(g1a.x()), worldToScreenX(g1b.x()),
                        worldToScreenX(g2b.x()), worldToScreenX(g2a.x())},
                new double[]{worldToScreenY(g1a.y()), worldToScreenY(g1b.y()),
                        worldToScreenY(g2b.y()), worldToScreenY(g2a.y())},
                4
        );
        CadStyle.applySharpStroke(g, Math.max(0.6, lw * 0.85));
        g.setStroke(selected ? CadStyle.WALL_SEL : CadStyle.ACCENT);
        g.strokeLine(worldToScreenX(g1a.x()), worldToScreenY(g1a.y()),
                worldToScreenX(g1b.x()), worldToScreenY(g1b.y()));
        g.strokeLine(worldToScreenX(g2a.x()), worldToScreenY(g2a.y()),
                worldToScreenX(g2b.x()), worldToScreenY(g2b.y()));
    }

    private void drawPreview(GraphicsContext g) {
        CadStyle.applyCadStroke(g, CadStyle.lineWeight(1.4, scale));
        g.setLineDashes(6, 4);
        g.setStroke(CadStyle.PREVIEW);
        if (wallStart != null && previewEnd != null) {
            Vec2 a = wallStart;
            Vec2 b = previewEnd;
            Vec2 n = b.subtract(a).perpendicular();
            if (n.length() > 0.5) {
                double halfT = CadStyle.wallHalfMm(CadStyle.DEFAULT_WALL_MM, scale);
                Vec2 a1 = a.add(n.scale(halfT));
                Vec2 a2 = a.add(n.scale(-halfT));
                Vec2 b1 = b.add(n.scale(halfT));
                Vec2 b2 = b.add(n.scale(-halfT));
                g.setFill(Color.web("#00ff88", 0.14));
                g.fillPolygon(
                        new double[]{worldToScreenX(a1.x()), worldToScreenX(b1.x()),
                                worldToScreenX(b2.x()), worldToScreenX(a2.x())},
                        new double[]{worldToScreenY(a1.y()), worldToScreenY(b1.y()),
                                worldToScreenY(b2.y()), worldToScreenY(a2.y())},
                        4
                );
            }
            g.strokeLine(
                    worldToScreenX(wallStart.x()), worldToScreenY(wallStart.y()),
                    worldToScreenX(previewEnd.x()), worldToScreenY(previewEnd.y())
            );
            double len = wallStart.distanceTo(previewEnd);
            g.setLineDashes(null);
            g.setFill(CadStyle.PREVIEW);
            g.setFont(CadStyle.labelFont(Math.clamp(10 * (scale / 0.06), 9, 13)));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(String.format("%.0f mm  (%.2f m)", len, len / 1000.0),
                    worldToScreenX(previewEnd.x()) + 10,
                    worldToScreenY(previewEnd.y()) - 10);
        }
        if (roomOrigin != null && roomPreviewCorner != null) {
            double x = Math.min(roomOrigin.x(), roomPreviewCorner.x());
            double y = Math.min(roomOrigin.y(), roomPreviewCorner.y());
            double rw = Math.abs(roomOrigin.x() - roomPreviewCorner.x());
            double rh = Math.abs(roomOrigin.y() - roomPreviewCorner.y());
            g.setFill(Color.web("#00ff88", 0.08));
            g.fillRect(worldToScreenX(x), worldToScreenY(y), rw * scale, rh * scale);
            g.strokeRect(worldToScreenX(x), worldToScreenY(y), rw * scale, rh * scale);
            g.setLineDashes(null);
            g.setFill(CadStyle.PREVIEW);
            g.setFont(CadStyle.labelFont(11));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(String.format("%.0f × %.0f mm  (%.1f m²)", rw, rh, (rw * rh) / 1_000_000.0),
                    worldToScreenX(x) + 8, worldToScreenY(y) + 16);
        }
        g.setLineDashes(null);
    }

    private void drawWiringRoutes(GraphicsContext g) {
        if (!floorPlan.isShowWiringRoutes() || floorPlan.wiringRoutes().isEmpty()) {
            return;
        }
        int i = 0;
        for (WiringRoute route : floorPlan.wiringRoutes()) {
            List<Vec2> pts = route.points();
            if (pts.size() < 2) {
                continue;
            }
            // Layer-style colours (AutoCAD circuit layers)
            double hue = (i * 47) % 360;
            Color routeColor = Color.hsb(hue, 0.65, 0.95, 0.90);
            CadStyle.applyCadStroke(g, CadStyle.lineWeight(1.2, scale));
            g.setStroke(routeColor);
            g.setLineDashes(10, 5);
            strokePolyline(g, pts);
            g.setLineDashes(null);
            // Small vertices
            g.setFill(routeColor);
            double r = Math.max(1.5, CadStyle.lineWeight(1.5, scale));
            for (Vec2 p : pts) {
                double sx = worldToScreenX(p.x());
                double sy = worldToScreenY(p.y());
                g.fillOval(sx - r, sy - r, r * 2, r * 2);
            }
            i++;
        }
    }

    private void strokePolyline(GraphicsContext g, List<Vec2> pts) {
        g.beginPath();
        g.moveTo(worldToScreenX(pts.get(0).x()), worldToScreenY(pts.get(0).y()));
        for (int p = 1; p < pts.size(); p++) {
            g.lineTo(worldToScreenX(pts.get(p).x()), worldToScreenY(pts.get(p).y()));
        }
        g.stroke();
    }

    private void drawDevices(GraphicsContext g) {
        double size = SymbolRenderer.screenSize(scale);
        double pad = size + 8;
        double viewW = canvas.getWidth();
        double viewH = canvas.getHeight();
        for (PlacedDevice d : floorPlan.devices()) {
            double sx = worldToScreenX(d.xMm());
            double sy = worldToScreenY(d.yMm());
            // Viewport cull — skip drawing off-screen symbols (still O(n) scan)
            if (sx < -pad || sy < -pad || sx > viewW + pad || sy > viewH + pad) {
                continue;
            }
            boolean selected = selection.kind() == SelectionModel.Kind.DEVICE
                    && selection.device() != null
                    && selection.device().id().equals(d.id());
            SymbolRenderer.draw(
                    g,
                    d.symbolKey(),
                    sx,
                    sy,
                    size,
                    d.rotationDeg(),
                    selected
            );
        }
    }

    private void drawDimensions(GraphicsContext g) {
        for (LinearDimension dim : floorPlan.dimensions()) {
            drawOneDimension(g, dim.p1(), dim.p2(), dim.offsetMm(), dim.displayLabel());
        }
        if (getTool() == DrawTool.DIMENSION && dimStart != null && previewEnd != null) {
            drawOneDimension(g, dimStart, previewEnd, 400,
                    String.format(java.util.Locale.ROOT, "%.0f mm", dimStart.distanceTo(previewEnd)));
        }
    }

    private void drawOneDimension(GraphicsContext g, Vec2 a, Vec2 b, double offsetMm, String label) {
        Vec2 dir = b.subtract(a);
        if (dir.length() < 1e-3) {
            return;
        }
        Vec2 n = dir.perpendicular();
        Vec2 o = n.scale(offsetMm);
        Vec2 a1 = a.add(o);
        Vec2 b1 = b.add(o);
        g.setStroke(Color.web("#88ccee"));
        g.setLineWidth(1.2);
        g.strokeLine(worldToScreenX(a.x()), worldToScreenY(a.y()),
                worldToScreenX(a1.x()), worldToScreenY(a1.y()));
        g.strokeLine(worldToScreenX(b.x()), worldToScreenY(b.y()),
                worldToScreenX(b1.x()), worldToScreenY(b1.y()));
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(b1.x()), worldToScreenY(b1.y()));
        // Arrow ticks
        double tick = 80;
        Vec2 tDir = dir.normalize().scale(tick);
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(a1.add(tDir).x()), worldToScreenY(a1.add(tDir).y()));
        g.strokeLine(worldToScreenX(b1.x()), worldToScreenY(b1.y()),
                worldToScreenX(b1.subtract(tDir).x()), worldToScreenY(b1.subtract(tDir).y()));
        Vec2 mid = a1.lerp(b1, 0.5);
        g.setFill(Color.web("#88ccee"));
        g.setFont(CadStyle.labelFont(Math.clamp(11 * (scale / 0.06), 9, 14)));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(label, worldToScreenX(mid.x()), worldToScreenY(mid.y()) - 4);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void drawGrips(GraphicsContext g) {
        double r = Math.max(4, 6);
        g.setFill(Color.web("#00ffcc"));
        g.setStroke(Color.web("#003322"));
        g.setLineWidth(1);
        if (selection.kind() == SelectionModel.Kind.WALL && selection.wall() != null) {
            Wall w = selection.wall();
            gripSquare(g, w.start(), r);
            gripSquare(g, w.end(), r);
        } else if (selection.kind() == SelectionModel.Kind.ROOM && selection.room() != null) {
            Room room = selection.room();
            gripSquare(g, new Vec2(room.x(), room.y()), r);
            gripSquare(g, new Vec2(room.x() + room.widthMm(), room.y()), r);
            gripSquare(g, new Vec2(room.x() + room.widthMm(), room.y() + room.heightMm()), r);
            gripSquare(g, new Vec2(room.x(), room.y() + room.heightMm()), r);
        }
    }

    private void gripSquare(GraphicsContext g, Vec2 world, double r) {
        double sx = worldToScreenX(world.x());
        double sy = worldToScreenY(world.y());
        g.fillRect(sx - r, sy - r, r * 2, r * 2);
        g.strokeRect(sx - r, sy - r, r * 2, r * 2);
    }

    private void drawHud(GraphicsContext g, double w, double h) {
        int drawingScale = CadStyle.approxDrawingScale(scale);
        String place = movingDevice != null
                ? " · move " + movingDevice.displayName()
                : (dropHighlight ? " · drop to place" : "");
        String status = "1:%d  |  grid %.0f mm  |  OSNAP %s  |  Ortho %s  |  %d devices%s".formatted(
                drawingScale,
                floorPlan.gridMm(),
                cadSettings.isEndpointSnap() ? "ON" : "off",
                isOrthoActive() ? "ON" : "off",
                floorPlan.devices().size(),
                place);

        g.setFont(CadStyle.smallFont(10));
        double chipW = Math.min(w - 20, Math.max(280, status.length() * 6.0 + 20));
        double chipH = 24;
        double chipX = 10;
        double chipY = h - 36;
        g.setFill(CadStyle.HUD_BG);
        g.fillRect(chipX, chipY, chipW, chipH);
        CadStyle.applySharpStroke(g, 0.8);
        g.setStroke(CadStyle.HUD_BORDER);
        g.strokeRect(chipX + 0.5, chipY + 0.5, chipW - 1, chipH - 1);
        g.setFill(CadStyle.HUD_TEXT);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(status, chipX + 10, chipY + 16);

        drawScaleBar(g, w, h);
    }

    private void drawScaleBar(GraphicsContext g, double w, double h) {
        double barMm = CadStyle.niceScaleBarMm(scale);
        double barPx = barMm * scale;
        if (barPx < 36 || barPx > w * 0.42) {
            return;
        }
        double margin = 14;
        double x1 = w - margin - barPx;
        double x2 = w - margin;
        double y = h - 20;
        double tick = 5;

        g.setFill(CadStyle.HUD_BG);
        g.fillRect(x1 - 8, y - 16, barPx + 16, 24);
        CadStyle.applySharpStroke(g, 0.8);
        g.setStroke(CadStyle.HUD_BORDER);
        g.strokeRect(x1 - 7.5, y - 15.5, barPx + 15, 23);

        int segs = 4;
        double segPx = barPx / segs;
        for (int i = 0; i < segs; i++) {
            g.setFill(i % 2 == 0 ? CadStyle.SCALE_TICK : Color.web("#333333"));
            g.fillRect(x1 + i * segPx, y - 2.5, segPx, 5);
        }
        CadStyle.applySharpStroke(g, 1.0);
        g.setStroke(CadStyle.SCALE_TICK);
        g.strokeLine(x1, y, x2, y);
        g.strokeLine(x1, y - tick, x1, y + 1);
        g.strokeLine(x2, y - tick, x2, y + 1);

        g.setFont(CadStyle.smallFont(9));
        g.setFill(CadStyle.HUD_TEXT);
        g.setTextAlign(TextAlignment.CENTER);
        String label = barMm >= 1000
                ? String.format("%.0f m", barMm / 1000.0)
                : String.format("%.0f mm", barMm);
        g.fillText("1:" + CadStyle.approxDrawingScale(scale) + "  ·  " + label, (x1 + x2) / 2, y - 7);
        g.setTextAlign(TextAlignment.LEFT);
    }
}
