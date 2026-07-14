package com.ghana.gwire.domain.geometry;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Wall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uniform grid spatial index for plan hit-testing at high device counts (Phase 16).
 * Cell size is in plan millimetres.
 */
public final class SpatialIndex {

    /** Use linear scan below this count (index overhead not worth it). */
    public static final int DEVICE_INDEX_THRESHOLD = 80;

    private final double cellSizeMm;
    private final Map<Long, List<PlacedDevice>> deviceCells = new HashMap<>();
    private final Map<Long, List<Wall>> wallCells = new HashMap<>();

    public SpatialIndex(double cellSizeMm) {
        this.cellSizeMm = cellSizeMm > 50 ? cellSizeMm : 1000;
    }

    public static SpatialIndex forDevices(List<PlacedDevice> devices) {
        SpatialIndex idx = new SpatialIndex(1500);
        if (devices != null) {
            for (PlacedDevice d : devices) {
                idx.insertDevice(d);
            }
        }
        return idx;
    }

    public static SpatialIndex forWalls(List<Wall> walls) {
        SpatialIndex idx = new SpatialIndex(2000);
        if (walls != null) {
            for (Wall w : walls) {
                idx.insertWall(w);
            }
        }
        return idx;
    }

    public void insertDevice(PlacedDevice d) {
        if (d == null) {
            return;
        }
        long key = cellKey(d.xMm(), d.yMm());
        deviceCells.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
    }

    public void insertWall(Wall w) {
        if (w == null) {
            return;
        }
        // Insert into all cells the AABB of the segment covers
        double minX = Math.min(w.start().x(), w.end().x());
        double maxX = Math.max(w.start().x(), w.end().x());
        double minY = Math.min(w.start().y(), w.end().y());
        double maxY = Math.max(w.start().y(), w.end().y());
        int c0 = cellCoord(minX);
        int c1 = cellCoord(maxX);
        int r0 = cellCoord(minY);
        int r1 = cellCoord(maxY);
        for (int cx = c0; cx <= c1; cx++) {
            for (int cy = r0; cy <= r1; cy++) {
                wallCells.computeIfAbsent(pack(cx, cy), k -> new ArrayList<>()).add(w);
            }
        }
    }

    public Optional<PlacedDevice> nearestDevice(Vec2 p, double radiusMm) {
        if (p == null || radiusMm < 0) {
            return Optional.empty();
        }
        PlacedDevice best = null;
        double bestDist = radiusMm;
        int cells = Math.max(1, (int) Math.ceil(radiusMm / cellSizeMm));
        int cx = cellCoord(p.x());
        int cy = cellCoord(p.y());
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dy = -cells; dy <= cells; dy++) {
                List<PlacedDevice> bucket = deviceCells.get(pack(cx + dx, cy + dy));
                if (bucket == null) {
                    continue;
                }
                for (PlacedDevice d : bucket) {
                    double dist = d.distanceTo(p);
                    if (dist <= bestDist) {
                        bestDist = dist;
                        best = d;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<Wall> nearestWall(Vec2 p, double toleranceMm) {
        if (p == null || toleranceMm < 0) {
            return Optional.empty();
        }
        Wall best = null;
        double bestDist = toleranceMm;
        int cells = Math.max(1, (int) Math.ceil(toleranceMm / cellSizeMm)) + 1;
        int cx = cellCoord(p.x());
        int cy = cellCoord(p.y());
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dy = -cells; dy <= cells; dy++) {
                List<Wall> bucket = wallCells.get(pack(cx + dx, cy + dy));
                if (bucket == null) {
                    continue;
                }
                for (Wall w : bucket) {
                    double d = com.ghana.gwire.domain.geometry.Segment2.distancePointToSegment(
                            p, w.start(), w.end());
                    if (d <= bestDist) {
                        bestDist = d;
                        best = w;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private int cellCoord(double v) {
        return (int) Math.floor(v / cellSizeMm);
    }

    private long cellKey(double x, double y) {
        return pack(cellCoord(x), cellCoord(y));
    }

    private static long pack(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xffffffffL);
    }
}
