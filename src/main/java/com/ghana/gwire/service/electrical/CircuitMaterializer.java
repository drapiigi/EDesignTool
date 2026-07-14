package com.ghana.gwire.service.electrical;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.electrical.ConsumerUnit;
import com.ghana.gwire.domain.electrical.MountingHeights;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.WiringRoute;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.calc.AssumptionCollector;
import com.ghana.gwire.service.calc.CircuitBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds / upgrades the first-class circuit model from floor-plan devices (Phase 14).
 */
public final class CircuitMaterializer {

    private static final Logger log = LoggerFactory.getLogger(CircuitMaterializer.class);

    private CircuitMaterializer() {
    }

    /**
     * Ensure project has circuits + CU. If empty, materialize from {@link CircuitBuilder}.
     * Also applies default mounting heights and remaps wiring routes when possible.
     *
     * @return true if circuits were created/replaced
     */
    public static boolean ensureCircuits(Project project, Map<String, ElectricalComponent> catalogue) {
        return ensureCircuits(project, catalogue, null);
    }

    /**
     * Same as {@link #ensureCircuits(Project, Map)} but records length/load assumptions when
     * materializing (used by {@link com.ghana.gwire.service.calc.CalcEngine}).
     */
    public static boolean ensureCircuits(
            Project project,
            Map<String, ElectricalComponent> catalogue,
            AssumptionCollector assumptions
    ) {
        Objects.requireNonNull(project, "project");
        Map<String, ElectricalComponent> cat = catalogue == null ? Map.of() : catalogue;

        applyDefaultMountingHeights(project, cat);

        if (!project.circuits().isEmpty()) {
            syncDeviceCircuitIds(project);
            project.ensureConsumerUnit();
            return false;
        }

        materializeFromBuilder(project, cat, assumptions);
        return true;
    }

    /** Force rebuild circuits from current device layout (destructive to circuit list). */
    public static void rematerialize(Project project, Map<String, ElectricalComponent> catalogue) {
        Objects.requireNonNull(project, "project");
        Map<String, ElectricalComponent> cat = catalogue == null ? Map.of() : catalogue;
        // Clear device circuit links before rebuild
        for (BuildingStorey s : project.storeys()) {
            for (PlacedDevice d : s.floorPlan().devices()) {
                d.setCircuitId(null);
            }
        }
        materializeFromBuilder(project, cat, null);
    }

    private static void materializeFromBuilder(
            Project project,
            Map<String, ElectricalComponent> cat,
            AssumptionCollector assumptions
    ) {
        List<CircuitLoad> loads = CircuitBuilder.build(project, cat, assumptions);
        List<Circuit> circuits = new ArrayList<>();
        Map<String, String> oldToNew = new HashMap<>();

        for (CircuitLoad load : loads) {
            Circuit c = new Circuit(load.id(), load.name(), load.kind(), load.roomId());
            c.setDeviceIds(load.deviceIds());
            c.setEstimatedLengthM(load.estimatedLengthM());
            if (load.recommendedBreakerA() > 0) {
                c.setBreakerA(load.recommendedBreakerA());
            }
            if (load.recommendedCableId() != null) {
                c.setCableComponentId(load.recommendedCableId());
            }
            if (load.recommendedCableSize() != null) {
                c.setCableSize(load.recommendedCableSize());
            }
            circuits.add(c);
            oldToNew.put(load.id(), c.id());
            for (String deviceId : load.deviceIds()) {
                findDevice(project, deviceId).ifPresent(d -> d.setCircuitId(c.id()));
            }
        }

        project.setCircuits(circuits);
        ConsumerUnit cu = project.ensureConsumerUnit();
        int neededWays = Math.max(8, ((circuits.size() + 3) / 4) * 4);
        if (cu.ways() < neededWays) {
            cu.setWays(neededWays);
        }
        cu.assignCircuitsInOrder(circuits);

        remapWiringRoutes(project, circuits, oldToNew);
        log.info("Materialized {} circuit(s) and consumer unit ({} ways)", circuits.size(), cu.ways());
    }

    private static void remapWiringRoutes(
            Project project,
            List<Circuit> circuits,
            Map<String, String> oldLoadIdToCircuitId
    ) {
        Map<String, Circuit> byId = new LinkedHashMap<>();
        for (Circuit c : circuits) {
            byId.put(c.id(), c);
        }
        for (BuildingStorey s : project.storeys()) {
            FloorPlan fp = s.floorPlan();
            for (WiringRoute route : fp.wiringRoutes()) {
                String rid = route.circuitId();
                if (rid == null) {
                    continue;
                }
                if (byId.containsKey(rid)) {
                    continue; // already stable
                }
                String mapped = oldLoadIdToCircuitId.get(rid);
                if (mapped != null) {
                    route.setCircuitId(mapped);
                } else {
                    // Try match by label
                    Circuit match = matchByLabel(circuits, route.label());
                    route.setCircuitId(match == null ? null : match.id());
                }
            }
        }
    }

    private static Circuit matchByLabel(List<Circuit> circuits, String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String l = label.toLowerCase();
        for (Circuit c : circuits) {
            if (c.name().toLowerCase().contains(l) || l.contains(c.name().toLowerCase())) {
                return c;
            }
        }
        return null;
    }

    private static void syncDeviceCircuitIds(Project project) {
        Set<String> known = new HashSet<>();
        for (Circuit c : project.circuits()) {
            known.add(c.id());
            for (String did : c.deviceIds()) {
                findDevice(project, did).ifPresent(d -> d.setCircuitId(c.id()));
            }
        }
        for (BuildingStorey s : project.storeys()) {
            for (PlacedDevice d : s.floorPlan().devices()) {
                if (d.circuitId() != null && !known.contains(d.circuitId())) {
                    d.setCircuitId(null);
                }
            }
        }
    }

    private static void applyDefaultMountingHeights(Project project, Map<String, ElectricalComponent> cat) {
        for (BuildingStorey s : project.storeys()) {
            for (PlacedDevice d : s.floorPlan().devices()) {
                if (d.mountingHeightMm() > 0) {
                    continue;
                }
                ElectricalComponent c = cat.get(d.componentId());
                ComponentCategory catg = c == null ? null : c.category();
                double h = MountingHeights.defaultFor(catg, d.symbolKey());
                if (h > 0) {
                    d.setMountingHeightMm(h);
                }
            }
        }
    }

    private static java.util.Optional<PlacedDevice> findDevice(Project project, String deviceId) {
        for (BuildingStorey s : project.storeys()) {
            var opt = s.floorPlan().findDevice(deviceId);
            if (opt.isPresent()) {
                return opt;
            }
        }
        return java.util.Optional.empty();
    }

    /** Convert persistent circuits to calc {@link CircuitLoad} list (preserves ids). */
    public static List<CircuitLoad> toCircuitLoads(Project project) {
        List<CircuitLoad> loads = new ArrayList<>();
        for (Circuit c : project.circuits()) {
            CircuitLoad load = new CircuitLoad(c.id(), c.name(), c.kind(), c.roomId());
            for (String did : c.deviceIds()) {
                load.addDeviceId(did);
            }
            load.setEstimatedLengthM(c.estimatedLengthM());
            if (c.breakerA() > 0) {
                load.setRecommendedBreakerA(c.breakerA());
            }
            if (c.cableComponentId() != null && !c.cableComponentId().isBlank()) {
                load.setRecommendedCableId(c.cableComponentId());
            }
            if (c.cableSize() != null && !c.cableSize().isBlank()) {
                load.setRecommendedCableSize(c.cableSize());
            }
            loads.add(load);
        }
        return loads;
    }

    /** Write calc results back onto persistent circuits. */
    public static void applyCalcResults(Project project, List<CircuitLoad> loads) {
        if (loads == null) {
            return;
        }
        Map<String, CircuitLoad> byId = new HashMap<>();
        for (CircuitLoad l : loads) {
            byId.put(l.id(), l);
        }
        for (Circuit c : project.circuits()) {
            CircuitLoad l = byId.get(c.id());
            if (l == null) {
                continue;
            }
            c.setEstimatedLengthM(l.estimatedLengthM());
            c.setBreakerA(l.recommendedBreakerA());
            if (l.recommendedCableId() != null) {
                c.setCableComponentId(l.recommendedCableId());
            }
            if (l.recommendedCableSize() != null) {
                c.setCableSize(l.recommendedCableSize());
            }
        }
    }
}
