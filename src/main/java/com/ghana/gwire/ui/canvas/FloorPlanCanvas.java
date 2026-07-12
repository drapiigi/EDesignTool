package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.service.history.FloorPlanHistory;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * High-performance-ish floor plan canvas: grid, pan/zoom, draw walls/rooms/openings.
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
        canvas.addEventHandler(ScrollEvent.SCROLL, this::onScroll);

        root.setFocusTraversable(true);
        root.addEventHandler(KeyEvent.KEY_PRESSED, this::onKey);
        root.setOnMouseClicked(e -> root.requestFocus());

        tool.addListener((o, a, b) -> {
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
        tool.set(t);
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
            case NONE -> {
            }
        }
        selection.clear();
        redraw();
        fireSelection();
        status("Deleted selection");
    }

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setFill(Color.web("#0e1219"));
        g.fillRect(0, 0, w, h);

        drawGrid(g, w, h);
        drawBackground(g);
        drawRooms(g);
        drawWalls(g);
        drawOpenings(g);
        drawPreview(g);
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
            case SELECT -> selectAt(world);
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

    private void onScroll(ScrollEvent e) {
        double factor = e.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
        zoomAt(e.getX(), e.getY(), factor);
        e.consume();
    }

    private void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
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

    private void selectAt(Vec2 world) {
        double tol = 200 / Math.max(scale, 0.01); // ~px tolerance in mm
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
        status(type + " placed on wall");
    }

    private void cancelInProgress() {
        wallStart = null;
        previewEnd = null;
        roomOrigin = null;
        roomPreviewCorner = null;
        panning = false;
    }

    private void status(String msg) {
        statusSink.accept(msg);
    }

    private void fireSelection() {
        selectionListener.run();
    }

    // --- drawing ---

    private void drawGrid(GraphicsContext g, double w, double h) {
        double grid = floorPlan.gridMm();
        double major = grid * 2;
        Vec2 topLeft = screenToWorld(0, 0);
        Vec2 bottomRight = screenToWorld(w, h);
        double x0 = Math.floor(topLeft.x() / grid) * grid;
        double y0 = Math.floor(topLeft.y() / grid) * grid;

        g.setLineWidth(1);
        for (double x = x0; x <= bottomRight.x(); x += grid) {
            boolean isMajor = Math.abs(x % major) < 1e-6 || Math.abs(x % major - major) < 1e-6;
            g.setStroke(isMajor ? Color.web("#2a3344") : Color.web("#1a2030"));
            double sx = worldToScreenX(x);
            g.strokeLine(sx, 0, sx, h);
        }
        for (double y = y0; y <= bottomRight.y(); y += grid) {
            boolean isMajor = Math.abs(y % major) < 1e-6 || Math.abs(y % major - major) < 1e-6;
            g.setStroke(isMajor ? Color.web("#2a3344") : Color.web("#1a2030"));
            double sy = worldToScreenY(y);
            g.strokeLine(0, sy, w, sy);
        }

        // Axes origin
        g.setStroke(Color.web("#2f9e6a", 0.5));
        g.setLineWidth(1.5);
        g.strokeLine(worldToScreenX(0), 0, worldToScreenX(0), h);
        g.strokeLine(0, worldToScreenY(0), w, worldToScreenY(0));
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
            g.setFill(selected ? Color.web("#2f9e6a", 0.22) : Color.web("#3d7ea6", 0.15));
            g.fillRect(x, y, w, h);
            g.setStroke(selected ? Color.web("#3ecf8e") : Color.web("#5b9fd4"));
            g.setLineWidth(selected ? 2.5 : 1.5);
            g.strokeRect(x, y, w, h);
            g.setFill(Color.web("#e8edf5"));
            g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
            g.fillText(r.name() + String.format(" (%.1f m²)", r.areaM2()), x + 6, y + 16);
        }
    }

    private void drawWalls(GraphicsContext g) {
        for (Wall wall : floorPlan.walls()) {
            boolean selected = selection.kind() == SelectionModel.Kind.WALL
                    && selection.wall() != null
                    && selection.wall().id().equals(wall.id());
            g.setStroke(selected ? Color.web("#f0b429") : Color.web("#e8edf5"));
            g.setLineWidth(selected ? 4 : 3);
            g.strokeLine(
                    worldToScreenX(wall.start().x()), worldToScreenY(wall.start().y()),
                    worldToScreenX(wall.end().x()), worldToScreenY(wall.end().y())
            );
            // End caps
            g.setFill(selected ? Color.web("#f0b429") : Color.web("#9aa6b8"));
            double r = selected ? 5 : 3.5;
            g.fillOval(worldToScreenX(wall.start().x()) - r, worldToScreenY(wall.start().y()) - r, r * 2, r * 2);
            g.fillOval(worldToScreenX(wall.end().x()) - r, worldToScreenY(wall.end().y()) - r, r * 2, r * 2);
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
            Vec2 perp = dir.perpendicular();
            double half = o.widthMm() / 2.0;
            Vec2 a = center.add(dir.scale(-half));
            Vec2 b = center.add(dir.scale(half));

            boolean selected = selection.kind() == SelectionModel.Kind.OPENING
                    && selection.opening() != null
                    && selection.opening().id().equals(o.id());
            boolean door = o.type() == OpeningType.DOOR;
            g.setStroke(selected ? Color.web("#f0b429") : (door ? Color.web("#e07050") : Color.web("#50b0e0")));
            g.setLineWidth(selected ? 3 : 2);
            // Gap in wall shown as thick opening mark + tick
            g.strokeLine(worldToScreenX(a.x()), worldToScreenY(a.y()),
                    worldToScreenX(b.x()), worldToScreenY(b.y()));
            Vec2 tick = perp.scale(door ? 400 : 200);
            g.strokeLine(
                    worldToScreenX(center.x()), worldToScreenY(center.y()),
                    worldToScreenX(center.x() + tick.x()), worldToScreenY(center.y() + tick.y())
            );
            g.setFill(g.getStroke());
            g.setFont(Font.font(10));
            g.fillText(door ? "D" : "W",
                    worldToScreenX(center.x()) + 4,
                    worldToScreenY(center.y()) - 4);
        }
    }

    private void drawPreview(GraphicsContext g) {
        g.setLineDashes(6, 4);
        g.setStroke(Color.web("#2f9e6a"));
        g.setLineWidth(2);
        if (wallStart != null && previewEnd != null) {
            g.strokeLine(
                    worldToScreenX(wallStart.x()), worldToScreenY(wallStart.y()),
                    worldToScreenX(previewEnd.x()), worldToScreenY(previewEnd.y())
            );
            double len = wallStart.distanceTo(previewEnd);
            g.setLineDashes(null);
            g.setFill(Color.web("#2f9e6a"));
            g.fillText(String.format("%.0f mm", len),
                    worldToScreenX(previewEnd.x()) + 8,
                    worldToScreenY(previewEnd.y()) - 8);
        }
        if (roomOrigin != null && roomPreviewCorner != null) {
            double x = Math.min(roomOrigin.x(), roomPreviewCorner.x());
            double y = Math.min(roomOrigin.y(), roomPreviewCorner.y());
            double w = Math.abs(roomOrigin.x() - roomPreviewCorner.x());
            double h = Math.abs(roomOrigin.y() - roomPreviewCorner.y());
            g.strokeRect(worldToScreenX(x), worldToScreenY(y), w * scale, h * scale);
            g.setLineDashes(null);
            g.setFill(Color.web("#2f9e6a"));
            g.fillText(String.format("%.0f × %.0f mm", w, h),
                    worldToScreenX(x) + 8, worldToScreenY(y) + 16);
        }
        g.setLineDashes(null);
    }

    private void drawHud(GraphicsContext g, double w, double h) {
        g.setFill(Color.web("#12161e", 0.75));
        g.fillRoundRect(10, h - 34, 300, 24, 6, 6);
        g.setFill(Color.web("#9aa6b8"));
        g.setFont(Font.font(11));
        g.fillText("Grid %.0f mm · snap %s · %.3f px/mm".formatted(
                        floorPlan.gridMm(),
                        floorPlan.isSnapToGrid() ? "on" : "off",
                        scale),
                18, h - 17);
    }
}
