package com.ghana.gwire.domain.floorplan;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Vector floor plan: walls, rooms, openings, placed devices, optional raster background.
 * Coordinates are millimetres in a plan-local XY frame.
 */
public final class FloorPlan {

    private final List<Wall> walls = new ArrayList<>();
    private final List<Room> rooms = new ArrayList<>();
    private final List<Opening> openings = new ArrayList<>();
    private final List<PlacedDevice> devices = new ArrayList<>();
    private BackgroundImage background;
    private double gridMm = 500;
    private boolean snapToGrid = true;

    public List<Wall> walls() {
        return Collections.unmodifiableList(walls);
    }

    public List<Room> rooms() {
        return Collections.unmodifiableList(rooms);
    }

    public List<Opening> openings() {
        return Collections.unmodifiableList(openings);
    }

    public List<PlacedDevice> devices() {
        return Collections.unmodifiableList(devices);
    }

    public BackgroundImage background() {
        return background;
    }

    public void setBackground(BackgroundImage background) {
        this.background = background;
    }

    public void clearBackground() {
        this.background = null;
    }

    public double gridMm() {
        return gridMm;
    }

    public void setGridMm(double gridMm) {
        this.gridMm = Math.max(50, gridMm);
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
    }

    public Vec2 snap(Vec2 p) {
        return snapToGrid ? p.snap(gridMm) : p;
    }

    public void addWall(Wall wall) {
        walls.add(wall);
    }

    public void addRoom(Room room) {
        rooms.add(room);
    }

    public void addOpening(Opening opening) {
        openings.add(opening);
    }

    public void addDevice(PlacedDevice device) {
        devices.add(device);
    }

    public boolean removeWallById(String id) {
        openings.removeIf(o -> o.wallId().equals(id));
        return walls.removeIf(w -> w.id().equals(id));
    }

    public boolean removeRoomById(String id) {
        return rooms.removeIf(r -> r.id().equals(id));
    }

    public boolean removeOpeningById(String id) {
        return openings.removeIf(o -> o.id().equals(id));
    }

    public boolean removeDeviceById(String id) {
        return devices.removeIf(d -> d.id().equals(id));
    }

    public Optional<Wall> findWall(String id) {
        return walls.stream().filter(w -> w.id().equals(id)).findFirst();
    }

    public Optional<Room> findRoom(String id) {
        return rooms.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public Optional<Opening> findOpening(String id) {
        return openings.stream().filter(o -> o.id().equals(id)).findFirst();
    }

    public Optional<PlacedDevice> findDevice(String id) {
        return devices.stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public void clearGeometry() {
        walls.clear();
        rooms.clear();
        openings.clear();
        devices.clear();
    }

    /** Removes all placed devices; rooms, walls, and openings are kept. */
    public void clearDevices() {
        devices.clear();
    }

    public void clearAll() {
        clearGeometry();
        background = null;
    }

    /**
     * Hit-test walls within {@code toleranceMm}; returns nearest if any.
     */
    public Optional<Wall> hitWall(Vec2 p, double toleranceMm) {
        Wall best = null;
        double bestDist = toleranceMm;
        for (Wall w : walls) {
            double d = Segment2.distancePointToSegment(p, w.start(), w.end());
            if (d <= bestDist) {
                bestDist = d;
                best = w;
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<Room> hitRoom(Vec2 p) {
        // Top-most room in list wins
        for (int i = rooms.size() - 1; i >= 0; i--) {
            Room r = rooms.get(i);
            if (r.contains(p)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public Optional<Opening> hitOpening(Vec2 p, double toleranceMm) {
        Opening best = null;
        double bestDist = toleranceMm;
        for (Opening o : openings) {
            Optional<Wall> wall = findWall(o.wallId());
            if (wall.isEmpty()) {
                continue;
            }
            Wall w = wall.get();
            Vec2 center = w.start().lerp(w.end(), o.t());
            double d = p.distanceTo(center);
            if (d <= bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Hit-test placed devices within {@code radiusMm}; returns nearest if any.
     */
    public Optional<PlacedDevice> hitDevice(Vec2 p, double radiusMm) {
        PlacedDevice best = null;
        double bestDist = radiusMm;
        for (PlacedDevice d : devices) {
            double dist = d.distanceTo(p);
            if (dist <= bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return Optional.ofNullable(best);
    }

    public FloorPlan deepCopy() {
        FloorPlan copy = new FloorPlan();
        copy.gridMm = gridMm;
        copy.snapToGrid = snapToGrid;
        for (Wall w : walls) {
            copy.walls.add(w.copy());
        }
        for (Room r : rooms) {
            copy.rooms.add(r.copy());
        }
        for (Opening o : openings) {
            copy.openings.add(o.copy());
        }
        for (PlacedDevice d : devices) {
            copy.devices.add(d.copy());
        }
        if (background != null) {
            copy.background = background.copy();
        }
        return copy;
    }

    public void replaceFrom(FloorPlan other) {
        walls.clear();
        rooms.clear();
        openings.clear();
        devices.clear();
        gridMm = other.gridMm;
        snapToGrid = other.snapToGrid;
        for (Wall w : other.walls) {
            walls.add(w.copy());
        }
        for (Room r : other.rooms) {
            rooms.add(r.copy());
        }
        for (Opening o : other.openings) {
            openings.add(o.copy());
        }
        for (PlacedDevice d : other.devices) {
            devices.add(d.copy());
        }
        background = other.background == null ? null : other.background.copy();
    }
}
