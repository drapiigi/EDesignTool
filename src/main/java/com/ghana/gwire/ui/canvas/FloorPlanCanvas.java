package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.floorplan.WiringRoute;
import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.service.history.FloorPlanHistory;
import com.ghana.gwire.ui.symbols.ComponentDragFormats;
import com.ghana.gwire.ui.symbols.SymbolRenderer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.List;
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
    private final Map<String, Image> rasterCache = new HashMap<>();

    private FloorPlan floorPlan = new FloorPlan();
    private Consumer<String> statusSink = s -> {
    };
    private Runnable selectionListener = () -> {
    };
    private Runnable modelChangeListener = () -> {
    };

    /** Pixels per millimetre. */
    private double scale = 0.04;
    private double panX = 40;
    private double panY = 40;

    private Vec2 wallStart;
    private Vec2 dragStartWorld;
    private Vec2 roomOrigin;
    private boolean panning;
    private double panAnchorX;
    private double panAnchorY;
    private double panOriginX;
    private double panOriginY;

    // Rubber-band previews
    private Vec2 previewEnd;
    private Vec2 roomPreviewCorner;

    /** Catalogue component pending placement (PLACE_DEVICE tool fallback). */
    private ElectricalComponent pendingComponent;

    /** Live drag-move of a placed device (SELECT tool). */
    private PlacedDevice movingDevice;
    private boolean moveHistoryRecorded;
    private double moveGrabOffsetX;
    private double moveGrabOffsetY;
    private boolean dropHighlight;

    public FloorPlanCanvas(FloorPlanHistory history) {
        this.history = Objects.requireNonNull(history);
        canvas = new Canvas(800, 600);
        root = new Pane(canvas);
        root.getStyleClass().add("floor-plan-canvas-host");
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        canvas.widthProperty().addListener((o, a, b) -> redraw());
        canvas.heightProperty().addListener((o, a, b) -> redraw());

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
            status("Tool: " + b.label());
            redraw();
        });
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
        Vec2 pos = floorPlan.snap(Objects.requireNonNull(world, "world"));
        history.push(floorPlan);
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
        double worldW = Math.max(1000, maxX - minX);
        double worldH = Math.max(1000, maxY - minY);
        double pad = 40;
        double sx = (canvas.getWidth() - 2 * pad) / worldW;
        double sy = (canvas.getHeight() - 2 * pad) / worldH;
        scale = Math.clamp(Math.min(sx, sy), 0.005, 0.5);
        panX = pad - minX * scale;
        panY = pad - minY * scale;
        redraw();
        status("Fit view · scale %.3f px/mm".formatted(scale));
    }

    public void undo() {
        history.undo(floorPlan);
        selection.clear();
        cancelInProgress();
        redraw();
        fireSelection();
        status("Undo");
    }

    public void redo() {
        history.redo(floorPlan);
        selection.clear();
        cancelInProgress();
        redraw();
        fireSelection();
        status("Redo");
    }

    public void deleteSelection() {
        if (selection.isEmpty()) {
            return;
        }
        history.push(floorPlan);
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

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setImageSmoothing(true);
        g.setFill(CadStyle.PAPER);
        g.fillRect(0, 0, w, h);

        drawGrid(g, w, h);
        drawBackground(g);
        drawRooms(g);
        drawWalls(g);
        drawOpenings(g);
        drawWiringRoutes(g);
        drawDevices(g);
        drawPreview(g);
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

    private void onPress(MouseEvent e) {
        root.requestFocus();
        Vec2 world = floorPlan.snap(screenToWorld(e.getX(), e.getY()));

        if (e.getButton() == MouseButton.MIDDLE || getTool() == DrawTool.PAN) {
            panning = true;
            panAnchorX = e.getX();
            panAnchorY = e.getY();
            panOriginX = panX;
            panOriginY = panY;
            return;
        }

        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        switch (getTool()) {
            case SELECT -> beginSelectOrMove(world);
            case WALL -> {
                if (wallStart == null) {
                    wallStart = world;
                    previewEnd = world;
                    status("Wall: click end point (Esc cancel)");
                } else {
                    commitWall(wallStart, world);
                    wallStart = null;
                    previewEnd = null;
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
            case PAN -> {
                panning = true;
                panAnchorX = e.getX();
                panAnchorY = e.getY();
                panOriginX = panX;
                panOriginY = panY;
            }
        }
        redraw();
    }

    private void onDrag(MouseEvent e) {
        if (panning) {
            panX = panOriginX + (e.getX() - panAnchorX);
            panY = panOriginY + (e.getY() - panAnchorY);
            redraw();
            return;
        }
        // Live move placed device
        if (movingDevice != null) {
            Vec2 world = floorPlan.snap(screenToWorld(e.getX(), e.getY()));
            double nx = world.x() - moveGrabOffsetX;
            double ny = world.y() - moveGrabOffsetY;
            Vec2 snapped = floorPlan.snap(new Vec2(nx, ny));
            if (!moveHistoryRecorded) {
                history.push(floorPlan);
                // re-resolve device after deep-copy snapshot of prior state
                // (movingDevice still references live instance)
                moveHistoryRecorded = true;
            }
            movingDevice.setPosition(snapped.x(), snapped.y());
            floorPlan.hitRoom(snapped).ifPresentOrElse(
                    r -> movingDevice.setRoomId(r.id()),
                    () -> movingDevice.setRoomId(null)
            );
            selection.selectDevice(movingDevice);
            fireSelection();
            status("Moving %s → (%.0f, %.0f) mm"
                    .formatted(movingDevice.displayName(), snapped.x(), snapped.y()));
            redraw();
            return;
        }
        Vec2 world = floorPlan.snap(screenToWorld(e.getX(), e.getY()));
        if (getTool() == DrawTool.WALL && wallStart != null) {
            previewEnd = world;
            redraw();
        } else if (getTool() == DrawTool.ROOM && roomOrigin != null) {
            roomPreviewCorner = world;
            redraw();
        }
    }

    private void onRelease(MouseEvent e) {
        if (panning) {
            panning = false;
            return;
        }
        if (movingDevice != null) {
            PlacedDevice done = movingDevice;
            boolean moved = moveHistoryRecorded;
            movingDevice = null;
            moveHistoryRecorded = false;
            if (moved) {
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
        if (getTool() == DrawTool.WALL && wallStart != null) {
            previewEnd = floorPlan.snap(screenToWorld(e.getX(), e.getY()));
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

    private void beginSelectOrMove(Vec2 world) {
        double tol = 200 / Math.max(scale, 0.01);
        double deviceTol = Math.max(tol, SymbolRenderer.hitRadiusMm());
        var device = floorPlan.hitDevice(world, deviceTol);
        if (device.isPresent()) {
            PlacedDevice d = device.get();
            selection.selectDevice(d);
            fireSelection();
            // Start drag-move: grab offset so symbol doesn't jump under cursor
            movingDevice = d;
            moveHistoryRecorded = false;
            moveGrabOffsetX = world.x() - d.xMm();
            moveGrabOffsetY = world.y() - d.yMm();
            status("Drag to move · " + d.displayName());
            return;
        }
        movingDevice = null;
        selectAt(world);
    }

    private void selectAt(Vec2 world) {
        double tol = 200 / Math.max(scale, 0.01); // ~px tolerance in mm
        double deviceTol = Math.max(tol, SymbolRenderer.hitRadiusMm());
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
        history.push(floorPlan);
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
        history.push(floorPlan);
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
        history.push(floorPlan);
        Opening opening = new Opening(wall.id(), type, t, widthMm);
        floorPlan.addOpening(opening);
        selection.selectOpening(opening);
        fireSelection();
        fireModelChanged();
        status(type + " placed on wall");
    }

    private void cancelInProgress() {
        wallStart = null;
        previewEnd = null;
        roomOrigin = null;
        roomPreviewCorner = null;
        panning = false;
        movingDevice = null;
        moveHistoryRecorded = false;
        if (getTool() != DrawTool.PLACE_DEVICE) {
            pendingComponent = null;
        }
    }

    private void status(String msg) {
        statusSink.accept(msg);
    }

    private void fireSelection() {
        selectionListener.run();
    }

    // --- CAD drawing ---

    private void drawGrid(GraphicsContext g, double w, double h) {
        double grid = floorPlan.gridMm();
        double major = grid * 5; // every 5th minor = major (architectural feel)
        Vec2 topLeft = screenToWorld(0, 0);
        Vec2 bottomRight = screenToWorld(w, h);
        double x0 = Math.floor(topLeft.x() / grid) * grid;
        double y0 = Math.floor(topLeft.y() / grid) * grid;

        CadStyle.applySharpStroke(g, 1.0);
        for (double x = x0; x <= bottomRight.x() + grid * 0.5; x += grid) {
            boolean isMajor = Math.abs(Math.IEEEremainder(x, major)) < 1e-3;
            g.setStroke(isMajor ? CadStyle.GRID_MAJOR : CadStyle.GRID_MINOR);
            g.setLineWidth(isMajor ? 1.15 : 0.7);
            double sx = worldToScreenX(x);
            g.strokeLine(sx, 0, sx, h);
        }
        for (double y = y0; y <= bottomRight.y() + grid * 0.5; y += grid) {
            boolean isMajor = Math.abs(Math.IEEEremainder(y, major)) < 1e-3;
            g.setStroke(isMajor ? CadStyle.GRID_MAJOR : CadStyle.GRID_MINOR);
            g.setLineWidth(isMajor ? 1.15 : 0.7);
            double sy = worldToScreenY(y);
            g.strokeLine(0, sy, w, sy);
        }

        // Origin axes (plan 0,0)
        CadStyle.applySharpStroke(g, 1.4);
        g.setStroke(CadStyle.GRID_ORIGIN);
        g.strokeLine(worldToScreenX(0), 0, worldToScreenX(0), h);
        g.strokeLine(0, worldToScreenY(0), w, worldToScreenY(0));
        // origin mark
        double ox = worldToScreenX(0);
        double oy = worldToScreenY(0);
        g.setFill(CadStyle.GRID_ORIGIN);
        g.fillOval(ox - 3.5, oy - 3.5, 7, 7);
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

            // Soft hatch-like fill
            g.setFill(selected ? CadStyle.ROOM_FILL_SEL : CadStyle.ROOM_FILL);
            g.fillRect(x, y, w, h);

            CadStyle.applySharpStroke(g, selected ? 2.0 : 1.25);
            g.setStroke(selected ? CadStyle.ROOM_SEL : CadStyle.ROOM_EDGE);
            g.strokeRect(x + 0.5, y + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));

            // Corner ticks (CAD room annotation)
            double tick = Math.min(10, Math.min(w, h) * 0.08);
            g.setStroke(selected ? CadStyle.ROOM_SEL : CadStyle.DIM);
            CadStyle.applySharpStroke(g, 1.0);
            g.strokeLine(x, y, x + tick, y);
            g.strokeLine(x, y, x, y + tick);
            g.strokeLine(x + w, y, x + w - tick, y);
            g.strokeLine(x + w, y, x + w, y + tick);
            g.strokeLine(x, y + h, x + tick, y + h);
            g.strokeLine(x, y + h, x, y + h - tick);
            g.strokeLine(x + w, y + h, x + w - tick, y + h);
            g.strokeLine(x + w, y + h, x + w, y + h - tick);

            // Label block
            String title = r.name();
            String area = String.format("%.1f m²", r.areaM2());
            String dims = String.format("%.2f × %.2f m", r.widthMm() / 1000.0, r.heightMm() / 1000.0);
            g.setFont(CadStyle.labelFont(12));
            g.setTextAlign(TextAlignment.LEFT);
            g.setFill(CadStyle.ROOM_TEXT);
            double lx = x + 8;
            double ly = y + 16;
            g.fillText(title, lx, ly);
            g.setFont(CadStyle.smallFont(10));
            g.setFill(CadStyle.ROOM_DIM);
            g.fillText(area + "  ·  " + dims, lx, ly + 14);
        }
    }

    private void drawWalls(GraphicsContext g) {
        for (Wall wall : floorPlan.walls()) {
            boolean selected = selection.kind() == SelectionModel.Kind.WALL
                    && selection.wall() != null
                    && selection.wall().id().equals(wall.id());
            drawDoubleLineWall(g, wall, selected);
        }
    }

    /** Architectural double-line wall with filled core and crisp outlines. */
    private void drawDoubleLineWall(GraphicsContext g, Wall wall, boolean selected) {
        Vec2 a = wall.start();
        Vec2 b = wall.end();
        Vec2 dir = b.subtract(a);
        double len = dir.length();
        if (len < 1e-6) {
            return;
        }
        Vec2 n = dir.perpendicular(); // unit normal
        double halfT = Math.max(wall.thicknessMm(), 80) / 2.0;

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

        CadStyle.applySharpStroke(g, selected ? 1.8 : 1.15);
        g.setStroke(selected ? CadStyle.WALL_SEL : CadStyle.WALL_OUTLINE);
        g.strokePolygon(xs, ys, 4);

        // Centerline (construction reference) — dashed, subtle
        CadStyle.applySharpStroke(g, 0.8);
        g.setStroke(selected ? CadStyle.WALL_SEL.deriveColor(0, 1, 1, 0.55) : CadStyle.DIM);
        g.setLineDashes(4, 4);
        g.strokeLine(
                worldToScreenX(a.x()), worldToScreenY(a.y()),
                worldToScreenX(b.x()), worldToScreenY(b.y())
        );
        g.setLineDashes(null);

        // End nodes
        double r = selected ? 4.5 : 3.0;
        g.setFill(selected ? CadStyle.WALL_SEL : CadStyle.WALL);
        g.fillOval(worldToScreenX(a.x()) - r, worldToScreenY(a.y()) - r, r * 2, r * 2);
        g.fillOval(worldToScreenX(b.x()) - r, worldToScreenY(b.y()) - r, r * 2, r * 2);
        CadStyle.applySharpStroke(g, 1.0);
        g.setStroke(selected ? CadStyle.WALL_SEL : CadStyle.WALL_OUTLINE);
        g.strokeOval(worldToScreenX(a.x()) - r, worldToScreenY(a.y()) - r, r * 2, r * 2);
        g.strokeOval(worldToScreenX(b.x()) - r, worldToScreenY(b.y()) - r, r * 2, r * 2);
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
            double wallHalfT = Math.max(wall.thicknessMm(), 80) / 2.0;

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

    /** Plan door: clear opening + leaf + 90° swing arc (hinge at start). */
    private void drawDoorCad(
            GraphicsContext g, Vec2 a, Vec2 b, Vec2 dir, Vec2 perp,
            double wallHalfT, double widthMm, boolean selected
    ) {
        Color stroke = selected ? CadStyle.WALL_SEL : CadStyle.DOOR;
        Color swing = selected ? CadStyle.WALL_SEL.deriveColor(0, 1, 1, 0.45) : CadStyle.DOOR_SWING;

        // Clear wall section (paper fill over wall core)
        Vec2 a1 = a.add(perp.scale(wallHalfT + 4));
        Vec2 a2 = a.add(perp.scale(-(wallHalfT + 4)));
        Vec2 b1 = b.add(perp.scale(wallHalfT + 4));
        Vec2 b2 = b.add(perp.scale(-(wallHalfT + 4)));
        g.setFill(CadStyle.PAPER);
        g.fillPolygon(
                new double[]{worldToScreenX(a1.x()), worldToScreenX(b1.x()),
                        worldToScreenX(b2.x()), worldToScreenX(a2.x())},
                new double[]{worldToScreenY(a1.y()), worldToScreenY(b1.y()),
                        worldToScreenY(b2.y()), worldToScreenY(a2.y())},
                4
        );

        // Jambs
        CadStyle.applySharpStroke(g, selected ? 2.2 : 1.6);
        g.setStroke(stroke);
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(a2.x()), worldToScreenY(a2.y()));
        g.strokeLine(worldToScreenX(b1.x()), worldToScreenY(b1.y()),
                worldToScreenX(b2.x()), worldToScreenY(b2.y()));

        // Door leaf (closed, along wall) + swing arc
        double leafLen = widthMm;
        double rPx = leafLen * scale;
        double ax = worldToScreenX(a.x());
        double ay = worldToScreenY(a.y());
        // Leaf closed along wall toward b
        CadStyle.applyCadStroke(g, selected ? 2.4 : 1.8);
        g.setStroke(stroke);
        g.setLineCap(StrokeLineCap.BUTT);
        g.strokeLine(ax, ay, worldToScreenX(b.x()), worldToScreenY(b.y()));

        // Open leaf at 90° along perp
        Vec2 openEnd = a.add(perp.scale(leafLen));
        g.strokeLine(ax, ay, worldToScreenX(openEnd.x()), worldToScreenY(openEnd.y()));

        // Swing arc (JavaFX angles: 0 = east, CCW; screen Y grows down)
        g.setStroke(swing);
        CadStyle.applyCadStroke(g, 1.2);
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

        // Hinge node
        g.setFill(stroke);
        g.fillOval(ax - 2.5, ay - 2.5, 5, 5);

        g.setFont(CadStyle.smallFont(9));
        g.setFill(stroke);
        g.setTextAlign(TextAlignment.CENTER);
        Vec2 labelAt = a.add(b).scale(0.5).add(perp.scale(wallHalfT + 280));
        g.fillText("D", worldToScreenX(labelAt.x()), worldToScreenY(labelAt.y()));
    }

    /** Plan window: sill + double glazing lines across wall thickness. */
    private void drawWindowCad(
            GraphicsContext g, Vec2 a, Vec2 b, Vec2 dir, Vec2 perp,
            double wallHalfT, boolean selected
    ) {
        Color stroke = selected ? CadStyle.WALL_SEL : CadStyle.WINDOW;

        // Clear opening through wall
        Vec2 a1 = a.add(perp.scale(wallHalfT + 2));
        Vec2 a2 = a.add(perp.scale(-(wallHalfT + 2)));
        Vec2 b1 = b.add(perp.scale(wallHalfT + 2));
        Vec2 b2 = b.add(perp.scale(-(wallHalfT + 2)));
        g.setFill(CadStyle.PAPER);
        g.fillPolygon(
                new double[]{worldToScreenX(a1.x()), worldToScreenX(b1.x()),
                        worldToScreenX(b2.x()), worldToScreenX(a2.x())},
                new double[]{worldToScreenY(a1.y()), worldToScreenY(b1.y()),
                        worldToScreenY(b2.y()), worldToScreenY(a2.y())},
                4
        );

        // Outer frame
        CadStyle.applySharpStroke(g, selected ? 2.0 : 1.4);
        g.setStroke(stroke);
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(b1.x()), worldToScreenY(b1.y()));
        g.strokeLine(worldToScreenX(a2.x()), worldToScreenY(a2.y()),
                worldToScreenX(b2.x()), worldToScreenY(b2.y()));
        // Jambs
        g.strokeLine(worldToScreenX(a1.x()), worldToScreenY(a1.y()),
                worldToScreenX(a2.x()), worldToScreenY(a2.y()));
        g.strokeLine(worldToScreenX(b1.x()), worldToScreenY(b1.y()),
                worldToScreenX(b2.x()), worldToScreenY(b2.y()));

        // Double glazing lines (parallel to wall, inset from outer faces)
        double inset = wallHalfT * 0.35;
        Vec2 g1a = a.add(perp.scale(inset));
        Vec2 g1b = b.add(perp.scale(inset));
        Vec2 g2a = a.add(perp.scale(-inset));
        Vec2 g2b = b.add(perp.scale(-inset));
        g.setStroke(selected ? CadStyle.WALL_SEL : CadStyle.ACCENT);
        CadStyle.applySharpStroke(g, 1.1);
        g.strokeLine(worldToScreenX(g1a.x()), worldToScreenY(g1a.y()),
                worldToScreenX(g1b.x()), worldToScreenY(g1b.y()));
        g.strokeLine(worldToScreenX(g2a.x()), worldToScreenY(g2a.y()),
                worldToScreenX(g2b.x()), worldToScreenY(g2b.y()));

        // Glass fill between glazing lines
        g.setFill(CadStyle.WINDOW_GLASS);
        g.fillPolygon(
                new double[]{worldToScreenX(g1a.x()), worldToScreenX(g1b.x()),
                        worldToScreenX(g2b.x()), worldToScreenX(g2a.x())},
                new double[]{worldToScreenY(g1a.y()), worldToScreenY(g1b.y()),
                        worldToScreenY(g2b.y()), worldToScreenY(g2a.y())},
                4
        );

        g.setFont(CadStyle.smallFont(9));
        g.setFill(stroke);
        g.setTextAlign(TextAlignment.CENTER);
        Vec2 mid = a.add(b).scale(0.5).add(perp.scale(wallHalfT + 220));
        g.fillText("W", worldToScreenX(mid.x()), worldToScreenY(mid.y()));
    }

    private void drawPreview(GraphicsContext g) {
        CadStyle.applyCadStroke(g, 1.8);
        g.setLineDashes(7, 5);
        g.setStroke(CadStyle.PREVIEW);
        if (wallStart != null && previewEnd != null) {
            // Ghost double-line wall
            Vec2 a = wallStart;
            Vec2 b = previewEnd;
            Vec2 n = b.subtract(a).perpendicular();
            if (n.length() > 0.5) {
                double halfT = 75;
                Vec2 a1 = a.add(n.scale(halfT));
                Vec2 a2 = a.add(n.scale(-halfT));
                Vec2 b1 = b.add(n.scale(halfT));
                Vec2 b2 = b.add(n.scale(-halfT));
                g.setFill(Color.web("#a3be8c", 0.18));
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
            g.setFont(CadStyle.labelFont(11));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(String.format("%.0f mm  (%.2f m)", len, len / 1000.0),
                    worldToScreenX(previewEnd.x()) + 10,
                    worldToScreenY(previewEnd.y()) - 10);
        }
        if (roomOrigin != null && roomPreviewCorner != null) {
            double x = Math.min(roomOrigin.x(), roomPreviewCorner.x());
            double y = Math.min(roomOrigin.y(), roomPreviewCorner.y());
            double w = Math.abs(roomOrigin.x() - roomPreviewCorner.x());
            double h = Math.abs(roomOrigin.y() - roomPreviewCorner.y());
            g.setFill(Color.web("#a3be8c", 0.12));
            g.fillRect(worldToScreenX(x), worldToScreenY(y), w * scale, h * scale);
            g.strokeRect(worldToScreenX(x), worldToScreenY(y), w * scale, h * scale);
            g.setLineDashes(null);
            g.setFill(CadStyle.PREVIEW);
            g.setFont(CadStyle.labelFont(11));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText(String.format("%.0f × %.0f mm  (%.1f m²)", w, h, (w * h) / 1_000_000.0),
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
            double hue = (i * 47) % 360;
            Color routeColor = Color.hsb(hue, 0.50, 0.88, 0.88);
            // Soft outer stroke
            CadStyle.applyCadStroke(g, Math.max(2.5, 3.2));
            g.setStroke(routeColor.deriveColor(0, 1, 1, 0.25));
            g.setLineDashes(null);
            strokePolyline(g, pts);
            // Primary dashed conductor
            CadStyle.applyCadStroke(g, Math.max(1.3, 1.6));
            g.setStroke(routeColor);
            g.setLineDashes(8, 5);
            strokePolyline(g, pts);
            g.setLineDashes(null);
            // Nodes at vertices
            g.setFill(routeColor);
            for (Vec2 p : pts) {
                double sx = worldToScreenX(p.x());
                double sy = worldToScreenY(p.y());
                g.fillOval(sx - 2.2, sy - 2.2, 4.4, 4.4);
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
        for (PlacedDevice d : floorPlan.devices()) {
            boolean selected = selection.kind() == SelectionModel.Kind.DEVICE
                    && selection.device() != null
                    && selection.device().id().equals(d.id());
            SymbolRenderer.draw(
                    g,
                    d.symbolKey(),
                    worldToScreenX(d.xMm()),
                    worldToScreenY(d.yMm()),
                    size,
                    d.rotationDeg(),
                    selected
            );
        }
    }

    private void drawHud(GraphicsContext g, double w, double h) {
        // Status chip
        String place = movingDevice != null
                ? " · dragging " + movingDevice.displayName()
                : (dropHighlight ? " · drop to place" : "");
        String status = "Grid %.0f mm · snap %s · %.3f px/mm · %d devices%s".formatted(
                floorPlan.gridMm(),
                floorPlan.isSnapToGrid() ? "on" : "off",
                scale,
                floorPlan.devices().size(),
                place);

        g.setFont(CadStyle.smallFont(11));
        double textW = Math.max(320, status.length() * 6.2);
        double chipW = Math.min(w - 20, textW + 24);
        double chipH = 26;
        double chipX = 10;
        double chipY = h - 38;
        g.setFill(CadStyle.HUD_BG);
        g.fillRoundRect(chipX, chipY, chipW, chipH, 6, 6);
        CadStyle.applySharpStroke(g, 1.0);
        g.setStroke(CadStyle.HUD_BORDER);
        g.strokeRoundRect(chipX + 0.5, chipY + 0.5, chipW - 1, chipH - 1, 6, 6);
        g.setFill(CadStyle.HUD_TEXT);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(status, chipX + 12, chipY + 17);

        // Scale bar (bottom-right) — CAD drawing sheet style
        drawScaleBar(g, w, h);
    }

    private void drawScaleBar(GraphicsContext g, double w, double h) {
        double barMm = CadStyle.niceScaleBarMm(scale);
        double barPx = barMm * scale;
        if (barPx < 40 || barPx > w * 0.45) {
            return;
        }
        double margin = 16;
        double x1 = w - margin - barPx;
        double x2 = w - margin;
        double y = h - 22;
        double tick = 6;

        g.setFill(CadStyle.HUD_BG);
        g.fillRoundRect(x1 - 10, y - 18, barPx + 20, 28, 5, 5);
        CadStyle.applySharpStroke(g, 1.0);
        g.setStroke(CadStyle.HUD_BORDER);
        g.strokeRoundRect(x1 - 9.5, y - 17.5, barPx + 19, 27, 5, 5);

        // Alternating blocks (classic scale bar)
        int segs = 4;
        double segPx = barPx / segs;
        for (int i = 0; i < segs; i++) {
            g.setFill(i % 2 == 0 ? CadStyle.SCALE_TICK : CadStyle.DIM);
            g.fillRect(x1 + i * segPx, y - 3, segPx, 6);
        }
        CadStyle.applySharpStroke(g, 1.2);
        g.setStroke(CadStyle.SCALE_TICK);
        g.strokeLine(x1, y, x2, y);
        g.strokeLine(x1, y - tick, x1, y + 2);
        g.strokeLine(x2, y - tick, x2, y + 2);

        g.setFont(CadStyle.smallFont(10));
        g.setFill(CadStyle.HUD_TEXT);
        g.setTextAlign(TextAlignment.CENTER);
        String label = barMm >= 1000
                ? String.format("%.0f m", barMm / 1000.0)
                : String.format("%.0f mm", barMm);
        g.fillText(label, (x1 + x2) / 2, y - 8);
        g.setTextAlign(TextAlignment.LEFT);
    }
}
