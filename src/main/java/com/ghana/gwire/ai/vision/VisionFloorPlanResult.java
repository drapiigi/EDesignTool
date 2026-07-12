package com.ghana.gwire.ai.vision;

import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured result of vision floor-plan analysis, convertible into plan geometry.
 */
public final class VisionFloorPlanResult {

    private final List<VisionRoomHint> rooms;
    private final List<VisionWallHint> walls;
    private final List<VisionOpeningHint> openings;
    private final String notes;
    private final double confidence;
    private final String providerDetail;
    private final int sourcePixelWidth;
    private final int sourcePixelHeight;

    public VisionFloorPlanResult(
            List<VisionRoomHint> rooms,
            List<VisionWallHint> walls,
            List<VisionOpeningHint> openings,
            String notes,
            double confidence,
            String providerDetail,
            int sourcePixelWidth,
            int sourcePixelHeight
    ) {
        this.rooms = List.copyOf(rooms == null ? List.of() : rooms);
        this.walls = List.copyOf(walls == null ? List.of() : walls);
        this.openings = List.copyOf(openings == null ? List.of() : openings);
        this.notes = notes == null ? "" : notes;
        this.confidence = Math.clamp(confidence, 0, 1);
        this.providerDetail = providerDetail == null ? "" : providerDetail;
        this.sourcePixelWidth = Math.max(1, sourcePixelWidth);
        this.sourcePixelHeight = Math.max(1, sourcePixelHeight);
    }

    public List<VisionRoomHint> rooms() {
        return rooms;
    }

    public List<VisionWallHint> walls() {
        return walls;
    }

    public List<VisionOpeningHint> openings() {
        return openings;
    }

    public String notes() {
        return notes;
    }

    public double confidence() {
        return confidence;
    }

    public String providerDetail() {
        return providerDetail;
    }

    public int sourcePixelWidth() {
        return sourcePixelWidth;
    }

    public int sourcePixelHeight() {
        return sourcePixelHeight;
    }

    public boolean isEmpty() {
        return rooms.isEmpty() && walls.isEmpty();
    }

    public String summary() {
        return String.format(
                Locale.ROOT,
                "%d room(s), %d wall(s), %d opening(s) · confidence %.0f%% · %s",
                rooms.size(), walls.size(), openings.size(), confidence * 100, providerDetail
        );
    }

    /**
     * Applies detected geometry onto the floor plan (keeps background).
     *
     * @param clearExistingRoomsWalls if true, clear rooms/walls/openings (not background/devices unless clearDevices)
     * @param clearDevices            if true, also clear placed devices
     * @return number of rooms created
     */
    public int applyTo(
            FloorPlan plan,
            BackgroundImage background,
            boolean clearExistingRoomsWalls,
            boolean clearDevices
    ) {
        Objects.requireNonNull(plan, "plan");
        BackgroundImage bg = background != null ? background : plan.background();
        if (bg == null) {
            throw new IllegalStateException("Background image required to map vision coords to mm");
        }
        if (clearExistingRoomsWalls) {
            plan.clearRoomsWallsOpenings();
        }
        if (clearDevices) {
            plan.clearDevices();
        }

        double originX = bg.originXMm();
        double originY = bg.originYMm();
        double mpp = bg.mmPerPixel();
        double imgWmm = sourcePixelWidth * mpp;
        double imgHmm = sourcePixelHeight * mpp;

        int roomCount = 0;
        for (VisionRoomHint h : rooms) {
            double x = originX + h.xNorm() * imgWmm;
            double y = originY + h.yNorm() * imgHmm;
            double w = Math.max(500, h.widthNorm() * imgWmm);
            double hMm = Math.max(500, h.heightNorm() * imgHmm);
            String name = enrichName(h.name(), h.roomType());
            plan.addRoom(new Room(name, x, y, w, hMm));
            roomCount++;
        }

        List<Wall> createdWalls = new ArrayList<>();
        if (!walls.isEmpty()) {
            for (VisionWallHint wh : walls) {
                Vec2 a = new Vec2(originX + wh.x1Norm() * imgWmm, originY + wh.y1Norm() * imgHmm);
                Vec2 b = new Vec2(originX + wh.x2Norm() * imgWmm, originY + wh.y2Norm() * imgHmm);
                if (a.distanceTo(b) < 100) {
                    continue;
                }
                Wall wall = new Wall(a, b);
                plan.addWall(wall);
                createdWalls.add(wall);
            }
        } else {
            // Derive walls from room rectangles when vision omitted walls
            for (Room r : plan.rooms()) {
                addRectWalls(plan, createdWalls, r.x(), r.y(), r.widthMm(), r.heightMm());
            }
        }

        for (VisionOpeningHint oh : openings) {
            Vec2 p = new Vec2(originX + oh.xNorm() * imgWmm, originY + oh.yNorm() * imgHmm);
            Optional<Wall> wall = plan.hitWall(p, 400);
            if (wall.isEmpty() && !createdWalls.isEmpty()) {
                wall = nearestWall(createdWalls, p);
            }
            if (wall.isEmpty()) {
                continue;
            }
            Wall w = wall.get();
            double t = Segment2.closestT(p, w.start(), w.end());
            OpeningType type = oh.type().contains("WIN") ? OpeningType.WINDOW : OpeningType.DOOR;
            double widthMm = Math.max(600, oh.widthNorm() * imgWmm);
            plan.addOpening(new Opening(w.id(), type, t, widthMm));
        }

        return roomCount;
    }

    private static void addRectWalls(FloorPlan plan, List<Wall> out, double x, double y, double w, double h) {
        Vec2 tl = new Vec2(x, y);
        Vec2 tr = new Vec2(x + w, y);
        Vec2 br = new Vec2(x + w, y + h);
        Vec2 bl = new Vec2(x, y + h);
        for (Wall wall : List.of(new Wall(tl, tr), new Wall(tr, br), new Wall(br, bl), new Wall(bl, tl))) {
            plan.addWall(wall);
            out.add(wall);
        }
    }

    private static Optional<Wall> nearestWall(List<Wall> walls, Vec2 p) {
        Wall best = null;
        double bestD = Double.MAX_VALUE;
        for (Wall w : walls) {
            double d = Segment2.distancePointToSegment(p, w.start(), w.end());
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return Optional.ofNullable(best);
    }

    private static String enrichName(String name, String roomType) {
        if (roomType == null || roomType.isBlank()) {
            return name;
        }
        String n = name.toLowerCase(Locale.ROOT);
        String t = roomType.toLowerCase(Locale.ROOT);
        if (n.contains(t) || t.contains("unknown") || t.contains("other")) {
            return name;
        }
        // Prefer type-aware names for kitchen/bath rules in AI design
        if (t.contains("kitchen") && !n.contains("kitchen")) {
            return "Kitchen";
        }
        if ((t.contains("bath") || t.contains("toilet") || t.contains("wc"))
                && !n.contains("bath") && !n.contains("toilet") && !n.contains("wc")) {
            return "Bathroom";
        }
        return name;
    }

    public static VisionFloorPlanResult empty(String notes, int pw, int ph) {
        return new VisionFloorPlanResult(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                notes, 0, "empty", pw, ph
        );
    }
}
