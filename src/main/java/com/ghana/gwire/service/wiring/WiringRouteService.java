package com.ghana.gwire.service.wiring;

import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.WiringRoute;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.service.calc.CalcEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Generates simplified Manhattan wiring routes from distribution boards to loads (Phase 9).
 * Routes are schematic (not full conduit CAD) for layout visualisation.
 */
public final class WiringRouteService {

    private static final Logger log = LoggerFactory.getLogger(WiringRouteService.class);
    private final CalcEngine calcEngine = new CalcEngine();

    /**
     * Rebuild wiring routes on the active storey from the current calc report (or a new calc).
     *
     * @return number of routes created
     */
    public int generateForActiveStorey(Project project) {
        Objects.requireNonNull(project, "project");
        DesignReport report = project.lastReport();
        if (report == null) {
            try {
                report = calcEngine.calculate(project, LibraryBootstrap.get());
                project.setLastReport(report);
            } catch (Exception e) {
                log.warn("Calc for wiring failed: {}", e.getMessage());
                report = calcEngine.calculate(project, (ComponentLibraryService) null);
                project.setLastReport(report);
            }
        }
        return generateForFloor(project.floorPlan(), report);
    }

    /**
     * Generate routes for all storeys (each uses devices on that floor only; DB searched per floor).
     */
    public int generateForAllStoreys(Project project) {
        Objects.requireNonNull(project, "project");
        DesignReport report = project.lastReport();
        if (report == null) {
            report = calcEngine.calculate(project, (ComponentLibraryService) null);
            project.setLastReport(report);
        }
        int total = 0;
        for (BuildingStorey s : project.storeys()) {
            total += generateForFloor(s.floorPlan(), report);
        }
        return total;
    }

    public int generateForFloor(FloorPlan plan, DesignReport report) {
        Objects.requireNonNull(plan, "plan");
        plan.clearWiringRoutes();
        if (report == null || report.circuits().isEmpty()) {
            return 0;
        }

        Optional<PlacedDevice> db = findDb(plan);
        Vec2 dbPos = db.map(d -> new Vec2(d.xMm(), d.yMm()))
                .orElse(new Vec2(0, 0));

        Map<String, PlacedDevice> byId = new HashMap<>();
        for (PlacedDevice d : plan.devices()) {
            byId.put(d.id(), d);
        }

        int count = 0;
        for (CircuitLoad circuit : report.circuits()) {
            List<PlacedDevice> loads = new ArrayList<>();
            for (String id : circuit.deviceIds()) {
                PlacedDevice d = byId.get(id);
                if (d != null && !isInfrastructure(d)) {
                    loads.add(d);
                }
            }
            if (loads.isEmpty()) {
                continue;
            }
            for (PlacedDevice load : loads) {
                WiringRoute route = new WiringRoute(circuit.id(), circuit.name() + " → " + load.displayName());
                route.setCableComponentId(circuit.recommendedCableId());
                route.setPoints(manhattan(dbPos, new Vec2(load.xMm(), load.yMm())));
                plan.addWiringRoute(route);
                count++;
            }
        }
        log.info("Generated {} wiring routes on floor", count);
        return count;
    }

    /** Orthogonal path: start → (end.x, start.y) → end. */
    public static List<Vec2> manhattan(Vec2 from, Vec2 to) {
        List<Vec2> pts = new ArrayList<>(3);
        pts.add(from);
        if (Math.abs(from.x() - to.x()) > 1 || Math.abs(from.y() - to.y()) > 1) {
            pts.add(new Vec2(to.x(), from.y()));
        }
        pts.add(to);
        return pts;
    }

    private static Optional<PlacedDevice> findDb(FloorPlan plan) {
        for (PlacedDevice d : plan.devices()) {
            String key = d.symbolKey() == null ? "" : d.symbolKey().toLowerCase();
            String id = d.componentId() == null ? "" : d.componentId().toUpperCase();
            if (key.startsWith("db") || id.startsWith("DB-")) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    private static boolean isInfrastructure(PlacedDevice d) {
        String key = d.symbolKey() == null ? "" : d.symbolKey().toLowerCase();
        String id = d.componentId() == null ? "" : d.componentId().toUpperCase();
        return key.startsWith("db") || key.startsWith("rccb") || key.startsWith("rcbo")
                || key.startsWith("earth") || key.startsWith("spd") || key.startsWith("cable")
                || id.startsWith("DB-") || id.startsWith("RCCB") || id.startsWith("EARTH")
                || id.startsWith("SPD") || id.startsWith("CABLE");
    }
}
