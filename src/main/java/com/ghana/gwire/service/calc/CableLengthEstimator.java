package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.geometry.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Estimates final-circuit cable run lengths from floor-plan geometry.
 *
 * <p>Uses average Manhattan distance from nearest distribution board to each device,
 * with a routing allowance factor. Defaults apply when no DB is placed.
 */
public final class CableLengthEstimator {

    /** Extra routing factor for walls/ceilings (not pure plan distance). */
    public static final double ROUTING_FACTOR = 1.25;

    /** Fixed allowance added to each circuit length (m) for terminations / drops. */
    public static final double ALLOWANCE_M = 2.0;

    public static final double DEFAULT_SOCKET_M = 15.0;
    public static final double DEFAULT_LIGHTING_M = 12.0;
    public static final double DEFAULT_OTHER_M = 10.0;

    private CableLengthEstimator() {
    }

    /**
     * Collects plan positions of distribution boards among placed devices.
     *
     * @param devices    placed devices
     * @param catalogue  component id → catalogue entry (may be empty)
     */
    public static List<Vec2> findDistributionBoardPositions(
            Collection<PlacedDevice> devices,
            Map<String, ElectricalComponent> catalogue
    ) {
        List<Vec2> dbs = new ArrayList<>();
        if (devices == null) {
            return dbs;
        }
        Map<String, ElectricalComponent> cat = catalogue == null ? Map.of() : catalogue;
        for (PlacedDevice d : devices) {
            if (d == null) {
                continue;
            }
            ElectricalComponent c = cat.get(d.componentId());
            boolean isDb = false;
            if (c != null && c.category() == ComponentCategory.DISTRIBUTION_BOARD) {
                isDb = true;
            } else {
                String symbol = d.symbolKey() == null ? "" : d.symbolKey().toLowerCase();
                String cid = d.componentId() == null ? "" : d.componentId().toLowerCase();
                if (symbol.startsWith("db_") || cid.startsWith("db-") || cid.startsWith("db_")) {
                    isDb = true;
                }
            }
            if (isDb) {
                dbs.add(d.position());
            }
        }
        return dbs;
    }

    /**
     * Estimated circuit length in metres.
     *
     * @param devicePositions device positions in plan mm
     * @param dbPositions     DB positions in plan mm (may be empty)
     * @param kind            circuit kind for default when no DB
     */
    public static double estimateLengthM(
            List<Vec2> devicePositions,
            List<Vec2> dbPositions,
            CircuitKind kind
    ) {
        return estimateLengthM(devicePositions, dbPositions, kind, null);
    }

    public static double estimateLengthM(
            List<Vec2> devicePositions,
            List<Vec2> dbPositions,
            CircuitKind kind,
            AssumptionCollector assumptions
    ) {
        if (devicePositions == null || devicePositions.isEmpty()) {
            add(assumptions, AssumptionCodes.LENGTH_DEFAULT_FALLBACK);
            return defaultLengthM(kind);
        }
        if (dbPositions == null || dbPositions.isEmpty()) {
            add(assumptions, AssumptionCodes.LENGTH_DEFAULT_FALLBACK);
            return defaultLengthM(kind);
        }

        double sumM = 0;
        int n = 0;
        for (Vec2 device : devicePositions) {
            if (device == null) {
                continue;
            }
            double nearestMm = Double.POSITIVE_INFINITY;
            for (Vec2 db : dbPositions) {
                if (db == null) {
                    continue;
                }
                double manMm = manhattanMm(db, device);
                if (manMm < nearestMm) {
                    nearestMm = manMm;
                }
            }
            if (Double.isFinite(nearestMm)) {
                sumM += nearestMm / 1000.0;
                n++;
            }
        }
        if (n == 0) {
            add(assumptions, AssumptionCodes.LENGTH_DEFAULT_FALLBACK);
            return defaultLengthM(kind);
        }
        add(assumptions, AssumptionCodes.LENGTH_FROM_DB_GEOMETRY);
        double avgM = sumM / n;
        return avgM * ROUTING_FACTOR + ALLOWANCE_M;
    }

    /**
     * Convenience: resolve device positions from a floor plan and device ids.
     */
    public static double estimateForDevices(
            FloorPlan plan,
            List<String> deviceIds,
            List<Vec2> dbPositions,
            CircuitKind kind
    ) {
        return estimateForDevices(plan, deviceIds, dbPositions, kind, null);
    }

    public static double estimateForDevices(
            FloorPlan plan,
            List<String> deviceIds,
            List<Vec2> dbPositions,
            CircuitKind kind,
            AssumptionCollector assumptions
    ) {
        Objects.requireNonNull(plan, "plan");
        List<Vec2> positions = new ArrayList<>();
        if (deviceIds != null) {
            for (String id : deviceIds) {
                plan.findDevice(id).ifPresent(d -> positions.add(d.position()));
            }
        }
        return estimateLengthM(positions, dbPositions, kind, assumptions);
    }

    private static void add(AssumptionCollector assumptions, String code) {
        if (assumptions != null) {
            assumptions.add(code);
        }
    }

    public static double defaultLengthM(CircuitKind kind) {
        if (kind == null) {
            return DEFAULT_OTHER_M;
        }
        return switch (kind) {
            case SOCKET -> DEFAULT_SOCKET_M;
            case LIGHTING -> DEFAULT_LIGHTING_M;
            default -> DEFAULT_OTHER_M;
        };
    }

    public static double manhattanMm(Vec2 a, Vec2 b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y());
    }
}
