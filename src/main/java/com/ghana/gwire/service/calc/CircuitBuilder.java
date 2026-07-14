package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.domain.project.ProjectSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Groups placed devices into final circuits and assigns connected loads, design currents,
 * and estimated cable lengths.
 */
public final class CircuitBuilder {

    private CircuitBuilder() {
    }

    /**
     * Build circuits from the project floor plan and component catalogue map.
     *
     * @param project   design project
     * @param catalogue componentId → catalogue entry (empty map allowed)
     * @return list of circuit loads (may be empty)
     */
    public static List<CircuitLoad> build(Project project, Map<String, ElectricalComponent> catalogue) {
        return build(project, catalogue, null);
    }

    public static List<CircuitLoad> build(
            Project project,
            Map<String, ElectricalComponent> catalogue,
            AssumptionCollector assumptions
    ) {
        Objects.requireNonNull(project, "project");
        Map<String, ElectricalComponent> cat = catalogue == null ? Map.of() : catalogue;
        // Active plan used for length estimator geometry; devices come from all storeys
        FloorPlan plan = project.floorPlan();
        double voltageV = project.settings().nominalVoltageV();
        if (voltageV <= 0) {
            voltageV = 230;
        }

        List<PlacedDevice> devices = new ArrayList<>();
        Map<String, Room> roomsById = new LinkedHashMap<>();
        for (BuildingStorey storey : project.storeys()) {
            FloorPlan fp = storey.floorPlan();
            devices.addAll(fp.devices());
            for (Room r : fp.rooms()) {
                roomsById.put(r.id(), r);
            }
        }
        List<Vec2> dbPositions = CableLengthEstimator.findDistributionBoardPositions(devices, cat);

        // Track devices already assigned to special circuits
        Map<String, Boolean> assigned = new LinkedHashMap<>();

        List<CircuitLoad> circuits = new ArrayList<>();

        // Special fixed loads first (cooker, water heater, AC) — own circuits
        for (PlacedDevice d : devices) {
            ElectricalComponent c = cat.get(d.componentId());
            CircuitKind special = classifySpecial(c, d);
            if (special == null) {
                continue;
            }
            String roomName = roomName(roomsById, d.roomId());
            String label = specialLabel(special, roomName, c, d);
            CircuitLoad cl = new CircuitLoad(label, special);
            cl.setRoomId(d.roomId());
            cl.addDeviceId(d.id());
            double power = LoadTables.assumedPowerW(c, d, assumptions);
            cl.setConnectedLoadW(power);
            cl.setDesignCurrentA(designCurrentA(power, voltageV, project.settings().supplyType(), special));
            cl.setEstimatedLengthM(
                    CableLengthEstimator.estimateForDevices(plan, List.of(d.id()), dbPositions, special, assumptions));
            circuits.add(cl);
            assigned.put(d.id(), true);
        }

        // Group lighting and sockets by room
        Map<String, List<PlacedDevice>> lightingByRoom = new LinkedHashMap<>();
        Map<String, List<PlacedDevice>> socketsByRoom = new LinkedHashMap<>();
        Map<String, List<PlacedDevice>> switchesByRoom = new LinkedHashMap<>();
        List<PlacedDevice> unassignedOther = new ArrayList<>();

        for (PlacedDevice d : devices) {
            if (assigned.containsKey(d.id())) {
                continue;
            }
            ElectricalComponent c = cat.get(d.componentId());
            ComponentCategory category = resolveCategory(c, d);

            if (isNonLoadInfrastructure(category)) {
                continue; // validation only later
            }

            String roomKey = d.roomId() == null || d.roomId().isBlank() ? "" : d.roomId();

            if (category == ComponentCategory.LIGHTING) {
                lightingByRoom.computeIfAbsent(roomKey, k -> new ArrayList<>()).add(d);
            } else if (category == ComponentCategory.SOCKET) {
                socketsByRoom.computeIfAbsent(roomKey, k -> new ArrayList<>()).add(d);
            } else if (category == ComponentCategory.SWITCH) {
                // Attach to lighting of same room later (0 W)
                switchesByRoom.computeIfAbsent(roomKey, k -> new ArrayList<>()).add(d);
            } else {
                unassignedOther.add(d);
            }
        }

        for (Map.Entry<String, List<PlacedDevice>> e : lightingByRoom.entrySet()) {
            String roomId = e.getKey().isEmpty() ? null : e.getKey();
            String roomName = roomName(roomsById, roomId);
            CircuitLoad cl = new CircuitLoad("Lighting - " + roomName, CircuitKind.LIGHTING);
            cl.setRoomId(roomId);
            double power = 0;
            List<String> ids = new ArrayList<>();
            List<Vec2> positions = new ArrayList<>();
            for (PlacedDevice d : e.getValue()) {
                ids.add(d.id());
                positions.add(d.position());
                power += LoadTables.assumedPowerW(cat.get(d.componentId()), d, assumptions);
                assigned.put(d.id(), true);
            }
            // Attach switches of same room (0 W load)
            List<PlacedDevice> switches = switchesByRoom.getOrDefault(e.getKey(), List.of());
            for (PlacedDevice sw : switches) {
                ids.add(sw.id());
                assigned.put(sw.id(), true);
            }
            cl.addDeviceIds(ids);
            cl.setConnectedLoadW(power);
            cl.setDesignCurrentA(designCurrentA(power, voltageV, project.settings().supplyType(), CircuitKind.LIGHTING));
            cl.setEstimatedLengthM(
                    CableLengthEstimator.estimateLengthM(positions, dbPositions, CircuitKind.LIGHTING, assumptions));
            circuits.add(cl);
        }

        // Switches with no lighting circuit in room → attach to global lighting or ignore load
        for (Map.Entry<String, List<PlacedDevice>> e : switchesByRoom.entrySet()) {
            for (PlacedDevice sw : e.getValue()) {
                if (!assigned.containsKey(sw.id())) {
                    // No lighting circuit: skip as load circuit (0 W control gear only)
                    assigned.put(sw.id(), true);
                }
            }
        }

        for (Map.Entry<String, List<PlacedDevice>> e : socketsByRoom.entrySet()) {
            String roomId = e.getKey().isEmpty() ? null : e.getKey();
            String roomName = roomName(roomsById, roomId);
            CircuitLoad cl = new CircuitLoad("Sockets - " + roomName, CircuitKind.SOCKET);
            cl.setRoomId(roomId);
            double power = 0;
            List<String> ids = new ArrayList<>();
            List<Vec2> positions = new ArrayList<>();
            for (PlacedDevice d : e.getValue()) {
                ids.add(d.id());
                positions.add(d.position());
                power += LoadTables.assumedPowerW(cat.get(d.componentId()), d, assumptions);
                assigned.put(d.id(), true);
            }
            cl.addDeviceIds(ids);
            cl.setConnectedLoadW(power);
            cl.setDesignCurrentA(designCurrentA(power, voltageV, project.settings().supplyType(), CircuitKind.SOCKET));
            cl.setEstimatedLengthM(
                    CableLengthEstimator.estimateLengthM(positions, dbPositions, CircuitKind.SOCKET, assumptions));
            circuits.add(cl);
        }

        // Remaining unassigned load devices: group by category globally
        Map<ComponentCategory, List<PlacedDevice>> byCat = new LinkedHashMap<>();
        for (PlacedDevice d : unassignedOther) {
            if (assigned.containsKey(d.id())) {
                continue;
            }
            ElectricalComponent c = cat.get(d.componentId());
            ComponentCategory catg = resolveCategory(c, d);
            if (isNonLoadInfrastructure(catg) || catg == ComponentCategory.SWITCH) {
                continue;
            }
            byCat.computeIfAbsent(catg, k -> new ArrayList<>()).add(d);
        }

        for (Map.Entry<ComponentCategory, List<PlacedDevice>> e : byCat.entrySet()) {
            CircuitKind kind = mapCategoryToKind(e.getKey());
            CircuitLoad cl = new CircuitLoad("Other - " + e.getKey().name(), kind);
            double power = 0;
            List<String> ids = new ArrayList<>();
            List<Vec2> positions = new ArrayList<>();
            for (PlacedDevice d : e.getValue()) {
                ids.add(d.id());
                positions.add(d.position());
                power += LoadTables.assumedPowerW(cat.get(d.componentId()), d, assumptions);
            }
            cl.addDeviceIds(ids);
            cl.setConnectedLoadW(power);
            cl.setDesignCurrentA(designCurrentA(power, voltageV, project.settings().supplyType(), kind));
            cl.setEstimatedLengthM(
                    CableLengthEstimator.estimateLengthM(positions, dbPositions, kind, assumptions));
            if (power > 0 || !ids.isEmpty()) {
                circuits.add(cl);
            }
        }

        return circuits;
    }

    /**
     * Design current: single-phase P/V for domestic; for THREE_PHASE_400V and 3ph special loads
     * use P / (√3 × V). Phase 4 defaults to P/V for most domestic circuits.
     */
    public static double designCurrentA(
            double powerW,
            double voltageV,
            ProjectSettings.SupplyType supplyType,
            CircuitKind kind
    ) {
        if (powerW <= 0 || voltageV <= 0) {
            return 0;
        }
        boolean threePhaseLoad = supplyType == ProjectSettings.SupplyType.THREE_PHASE_400V
                && (kind == CircuitKind.AC_OR_SPECIAL || kind == CircuitKind.DISTRIBUTION);
        if (threePhaseLoad) {
            return powerW / (Math.sqrt(3) * voltageV);
        }
        // Domestic final circuits treated as single-phase (230 V line-neutral even on 3ph supplies)
        double phaseV = supplyType == ProjectSettings.SupplyType.THREE_PHASE_400V
                && voltageV >= 380 ? 230.0 : voltageV;
        return powerW / phaseV;
    }

    static CircuitKind classifySpecial(ElectricalComponent c, PlacedDevice d) {
        String name = specialNameBlob(c, d);
        String lower = name.toLowerCase();
        String id = c == null ? "" : nullToEmpty(c.id()).toLowerCase();
        String symbol = c == null ? (d == null ? "" : nullToEmpty(d.symbolKey()).toLowerCase())
                : nullToEmpty(c.symbolKey()).toLowerCase();
        Double rating = c == null ? null : c.currentRatingA();
        ComponentCategory category = c == null ? null : c.category();

        if (lower.contains("cooker")
                || id.contains("45a")
                || symbol.contains("45a")
                || (rating != null && rating >= 45 && category == ComponentCategory.SWITCH)) {
            return CircuitKind.COOKER;
        }
        if (lower.contains("heater") || lower.contains("geyser") || lower.contains("immersion")) {
            return CircuitKind.WATER_HEATER;
        }
        if (lower.contains("air condition")
                || lower.contains("air-con")
                || lower.contains("aircon")
                || lower.matches(".*\\bac\\b.*")
                || symbol.contains("ac_")
                || id.contains("-ac")) {
            return CircuitKind.AC_OR_SPECIAL;
        }
        return null;
    }

    private static String specialNameBlob(ElectricalComponent c, PlacedDevice d) {
        StringBuilder sb = new StringBuilder();
        if (c != null) {
            sb.append(nullToEmpty(c.name())).append(' ').append(nullToEmpty(c.id()));
        }
        if (d != null) {
            sb.append(' ').append(nullToEmpty(d.nameOverride())).append(' ').append(nullToEmpty(d.symbolKey()));
        }
        return sb.toString();
    }

    private static String specialLabel(CircuitKind kind, String roomName, ElectricalComponent c, PlacedDevice d) {
        String base = switch (kind) {
            case COOKER -> "Cooker";
            case WATER_HEATER -> "Water heater";
            case AC_OR_SPECIAL -> "AC / special";
            default -> "Special";
        };
        if (roomName != null && !roomName.isBlank() && !"Unassigned".equals(roomName)) {
            return base + " - " + roomName;
        }
        if (c != null) {
            return base + " - " + c.name();
        }
        if (d != null) {
            return base + " - " + d.displayName();
        }
        return base;
    }

    private static String roomName(Map<String, Room> roomsById, String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return "Unassigned";
        }
        Room r = roomsById.get(roomId);
        return r == null ? "Unassigned" : r.name();
    }

    private static ComponentCategory resolveCategory(ElectricalComponent c, PlacedDevice d) {
        if (c != null) {
            return c.category();
        }
        // Infer from symbol when catalogue missing
        String symbol = d == null ? "" : nullToEmpty(d.symbolKey()).toLowerCase();
        if (symbol.startsWith("light") || symbol.contains("led") || symbol.contains("fluor")) {
            return ComponentCategory.LIGHTING;
        }
        if (symbol.startsWith("socket") || symbol.contains("sock")) {
            return ComponentCategory.SOCKET;
        }
        if (symbol.startsWith("switch")) {
            return ComponentCategory.SWITCH;
        }
        if (symbol.startsWith("db_")) {
            return ComponentCategory.DISTRIBUTION_BOARD;
        }
        return ComponentCategory.OTHER;
    }

    private static boolean isNonLoadInfrastructure(ComponentCategory category) {
        if (category == null) {
            return false;
        }
        return switch (category) {
            case CABLE, CIRCUIT_BREAKER, DISTRIBUTION_BOARD, PROTECTION,
                    EARTHING, CONDUIT, JUNCTION, ISOLATOR -> true;
            default -> false;
        };
    }

    private static CircuitKind mapCategoryToKind(ComponentCategory category) {
        if (category == null) {
            return CircuitKind.OTHER;
        }
        return switch (category) {
            case LIGHTING -> CircuitKind.LIGHTING;
            case SOCKET -> CircuitKind.SOCKET;
            default -> CircuitKind.OTHER;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
