package com.ghana.gwire.ai;

import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Offline rule-based electrical placement generator using illustrative Ghana residential
 * practice (L.I. 2008 context). Heuristics are for preliminary design only — verify with a CEWP.
 *
 * <p>Rules (illustrative):
 * <ul>
 *   <li>Lighting: {@code max(1, ceil(areaM2 / 12))} fittings near centre (staggered if multiple)</li>
 *   <li>Sockets: 2 / 3 / 4 twin outlets by room size, inset ~400 mm from walls</li>
 *   <li>Switch: 1-gang near left edge (door proxy), inset 200 mm</li>
 *   <li>Kitchen/cook: 45A DP cooker switch + extra twin socket</li>
 *   <li>Bath/toilet/WC: bulkhead light; at most one socket near dry edge</li>
 *   <li>Global once: consumer unit, RCCB, earth rod + clamp</li>
 * </ul>
 */
public final class RuleBasedDesignGenerator {

    private static final double SOCKET_INSET_MM = 400;
    private static final double SWITCH_INSET_MM = 200;
    private static final double LIGHT_INSET_MM = 300;

    public AiDesignPlan generate(Project project, List<ElectricalComponent> catalogue) {
        Objects.requireNonNull(project, "project");
        List<ElectricalComponent> cat = catalogue == null ? List.of() : catalogue;
        Map<String, ElectricalComponent> byId = indexById(cat);

        List<DesignPlacement> placements = new ArrayList<>();
        StringBuilder notes = new StringBuilder();
        notes.append("Rule-based Ghana residential placement (illustrative L.I. 2008 practice).\n");
        notes.append("Project: ").append(project.name())
                .append(" · ").append(project.supplySummary()).append('\n');

        List<Room> rooms = project.floorPlan().rooms();
        if (rooms.isEmpty()) {
            notes.append("No rooms on plan — only global protection/earthing devices placed.\n");
        }

        for (Room room : rooms) {
            placeForRoom(room, byId, cat, placements, notes);
        }

        placeGlobal(rooms, byId, cat, placements, notes);

        notes.append("Total placements: ").append(placements.size()).append('.');
        return new AiDesignPlan(
                AiDesignPlan.Source.RULES,
                notes.toString().trim(),
                placements,
                "RuleBasedDesignGenerator"
        );
    }

    private void placeForRoom(
            Room room,
            Map<String, ElectricalComponent> byId,
            List<ElectricalComponent> cat,
            List<DesignPlacement> out,
            StringBuilder notes
    ) {
        String nameLower = room.name().toLowerCase(Locale.ROOT);
        boolean wet = isWetRoom(nameLower);
        boolean kitchen = isKitchen(nameLower);
        double area = room.areaM2();

        notes.append(String.format(
                Locale.ROOT,
                "Room '%s' (%.1f m², %s): ",
                room.name(), area, wet ? "wet" : kitchen ? "kitchen" : "general"
        ));

        // --- Lighting ---
        int lightCount = Math.max(1, (int) Math.ceil(area / 12.0));
        ElectricalComponent lightComp = wet
                ? resolve(byId, cat, "LIGHT-BULKHEAD", ComponentCategory.LIGHTING, "bulkhead")
                : resolve(byId, cat, "LIGHT-LED-9W", ComponentCategory.LIGHTING, "led");
        if (lightComp != null) {
            for (int i = 0; i < lightCount; i++) {
                double[] xy = lightPosition(room, i, lightCount);
                out.add(placement(lightComp, lightComp.name() + " · " + room.name(),
                        xy[0], xy[1], room.id(), 0));
            }
            notes.append(lightCount).append(" light(s); ");
        } else {
            notes.append("no lighting in catalogue; ");
        }

        // --- Sockets ---
        int socketCount;
        if (wet) {
            socketCount = 1; // near dry edge only
        } else if (area < 10) {
            socketCount = 2;
        } else if (area < 20) {
            socketCount = 3;
        } else {
            socketCount = 4;
        }
        ElectricalComponent sock = resolve(byId, cat, "SOCK-13A-2G", ComponentCategory.SOCKET, "13a");
        if (sock != null && socketCount > 0) {
            for (int i = 0; i < socketCount; i++) {
                double[] xy = wallSocketPosition(room, i, socketCount);
                out.add(placement(sock, sock.name() + " · " + room.name(),
                        xy[0], xy[1], room.id(), 0));
            }
            notes.append(socketCount).append(" socket(s); ");
        } else if (sock == null) {
            notes.append("no sockets in catalogue; ");
        }

        // --- Switch (skip pure wet? still place for lighting control) ---
        ElectricalComponent sw = resolve(byId, cat, "SW-1G", ComponentCategory.SWITCH, "1-gang");
        if (sw != null) {
            double sx = room.x() + SWITCH_INSET_MM;
            double sy = room.y() + room.heightMm() / 2.0;
            // keep inside room
            sx = clamp(sx, room.x() + 50, room.x() + room.widthMm() - 50);
            sy = clamp(sy, room.y() + 50, room.y() + room.heightMm() - 50);
            out.add(placement(sw, sw.name() + " · " + room.name(), sx, sy, room.id(), 0));
            notes.append("1 switch; ");
        }

        // --- Kitchen extras ---
        if (kitchen) {
            ElectricalComponent cooker = resolve(byId, cat, "SW-45A-DP", ComponentCategory.SWITCH, "45a");
            if (cooker != null) {
                double cx = room.x() + room.widthMm() - SOCKET_INSET_MM;
                double cy = room.y() + SOCKET_INSET_MM;
                out.add(placement(cooker, "Cooker switch · " + room.name(),
                        cx, cy, room.id(), 0));
                notes.append("cooker DP; ");
            }
            if (sock != null) {
                double ex = room.x() + room.widthMm() / 2.0;
                double ey = room.y() + room.heightMm() - SOCKET_INSET_MM;
                out.add(placement(sock, "Extra twin socket · " + room.name(),
                        ex, ey, room.id(), 0));
                notes.append("extra kitchen socket; ");
            }
        }

        notes.append('\n');
    }

    private void placeGlobal(
            List<Room> rooms,
            Map<String, ElectricalComponent> byId,
            List<ElectricalComponent> cat,
            List<DesignPlacement> out,
            StringBuilder notes
    ) {
        double originX = 500;
        double originY = 500;
        if (!rooms.isEmpty()) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            for (Room r : rooms) {
                minX = Math.min(minX, r.x());
                minY = Math.min(minY, r.y());
            }
            originX = minX - 1500;
            originY = minY;
            if (!Double.isFinite(originX)) {
                originX = 500;
            }
            if (!Double.isFinite(originY)) {
                originY = 500;
            }
        }

        ElectricalComponent db = resolve(byId, cat, "DB-6WAY", ComponentCategory.DISTRIBUTION_BOARD, null);
        if (db != null) {
            out.add(placement(db, db.name(), originX, originY, null, 0));
            notes.append("DB at (").append(fmt(originX)).append(',').append(fmt(originY)).append("); ");
        } else {
            notes.append("no distribution board in catalogue; ");
        }

        ElectricalComponent rccb = resolve(byId, cat, "RCCB-40-30", ComponentCategory.PROTECTION, "rccb");
        if (rccb != null) {
            out.add(placement(rccb, rccb.name(), originX + 400, originY, null, 0));
            notes.append("RCCB near DB; ");
        } else {
            notes.append("no RCCB in catalogue; ");
        }

        double rodX = 0;
        double rodY = -1500;
        if (!rooms.isEmpty()) {
            double minX = rooms.stream().mapToDouble(Room::x).min().orElse(0);
            double minY = rooms.stream().mapToDouble(Room::y).min().orElse(0);
            rodX = minX;
            rodY = minY - 1500;
        }

        ElectricalComponent rod = resolve(byId, cat, "EARTH-ROD-16", ComponentCategory.EARTHING, "rod");
        if (rod != null) {
            out.add(placement(rod, rod.name(), rodX, rodY, null, 0));
            notes.append("earth rod; ");
        }

        ElectricalComponent clamp = resolve(byId, cat, "EARTH-CLAMP", ComponentCategory.EARTHING, "clamp");
        if (clamp != null) {
            out.add(placement(clamp, clamp.name(), rodX + 200, rodY, null, 0));
            notes.append("earth clamp; ");
        }

        notes.append('\n');
    }

    private static double[] lightPosition(Room room, int index, int total) {
        double cx = room.x() + room.widthMm() / 2.0;
        double cy = room.y() + room.heightMm() / 2.0;
        if (total <= 1) {
            return new double[]{cx, cy};
        }
        // Stagger along the longer axis
        boolean alongX = room.widthMm() >= room.heightMm();
        double span = alongX ? room.widthMm() : room.heightMm();
        double usable = Math.max(span - 2 * LIGHT_INSET_MM, span * 0.5);
        double step = usable / (total + 1);
        double offset = -usable / 2.0 + step * (index + 1);
        if (alongX) {
            return new double[]{
                    clamp(cx + offset, room.x() + LIGHT_INSET_MM, room.x() + room.widthMm() - LIGHT_INSET_MM),
                    cy
            };
        }
        return new double[]{
                cx,
                clamp(cy + offset, room.y() + LIGHT_INSET_MM, room.y() + room.heightMm() - LIGHT_INSET_MM)
        };
    }

    /**
     * Places sockets along walls in order: bottom, right, top, left (cyclic).
     */
    private static double[] wallSocketPosition(Room room, int index, int total) {
        int wall = index % 4;
        double t = total <= 1 ? 0.5 : (index + 1.0) / (total + 1.0);
        double x;
        double y;
        switch (wall) {
            case 0 -> { // bottom edge
                x = room.x() + room.widthMm() * t;
                y = room.y() + SOCKET_INSET_MM;
            }
            case 1 -> { // right edge
                x = room.x() + room.widthMm() - SOCKET_INSET_MM;
                y = room.y() + room.heightMm() * t;
            }
            case 2 -> { // top edge
                x = room.x() + room.widthMm() * (1.0 - t);
                y = room.y() + room.heightMm() - SOCKET_INSET_MM;
            }
            default -> { // left edge
                x = room.x() + SOCKET_INSET_MM;
                y = room.y() + room.heightMm() * (1.0 - t);
            }
        }
        x = clamp(x, room.x() + 50, room.x() + room.widthMm() - 50);
        y = clamp(y, room.y() + 50, room.y() + room.heightMm() - 50);
        // Snap-friendly 100 mm grid
        x = Math.round(x / 100.0) * 100.0;
        y = Math.round(y / 100.0) * 100.0;
        x = clamp(x, room.x() + 50, room.x() + room.widthMm() - 50);
        y = clamp(y, room.y() + 50, room.y() + room.heightMm() - 50);
        return new double[]{x, y};
    }

    private static DesignPlacement placement(
            ElectricalComponent c,
            String name,
            double x,
            double y,
            String roomId,
            double rot
    ) {
        return new DesignPlacement(c.id(), c.symbolKey(), name, x, y, roomId, rot);
    }

    /**
     * Resolve by preferred id, else first matching category (optional name fragment).
     */
    static ElectricalComponent resolve(
            Map<String, ElectricalComponent> byId,
            List<ElectricalComponent> cat,
            String preferredId,
            ComponentCategory category,
            String nameFragment
    ) {
        if (preferredId != null) {
            ElectricalComponent exact = byId.get(preferredId);
            if (exact != null) {
                return exact;
            }
        }
        String frag = nameFragment == null ? null : nameFragment.toLowerCase(Locale.ROOT);
        Optional<ElectricalComponent> match = cat.stream()
                .filter(c -> c.category() == category)
                .filter(c -> c.active())
                .filter(c -> frag == null
                        || c.id().toLowerCase(Locale.ROOT).contains(frag)
                        || c.name().toLowerCase(Locale.ROOT).contains(frag)
                        || c.symbolKey().toLowerCase(Locale.ROOT).contains(frag))
                .findFirst();
        if (match.isPresent()) {
            return match.get();
        }
        // Last resort: any in category
        return cat.stream()
                .filter(c -> c.category() == category && c.active())
                .findFirst()
                .orElse(null);
    }

    private static Map<String, ElectricalComponent> indexById(List<ElectricalComponent> cat) {
        Map<String, ElectricalComponent> map = new HashMap<>();
        for (ElectricalComponent c : cat) {
            map.put(c.id(), c);
        }
        return map;
    }

    private static boolean isWetRoom(String nameLower) {
        return nameLower.contains("bath")
                || nameLower.contains("toilet")
                || nameLower.contains("wc")
                || nameLower.contains("shower")
                || nameLower.contains("lavatory");
    }

    private static boolean isKitchen(String nameLower) {
        return nameLower.contains("kitchen") || nameLower.contains("cook");
    }

    private static double clamp(double v, double min, double max) {
        if (min > max) {
            return (min + max) / 2.0;
        }
        return Math.max(min, Math.min(max, v));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }
}
