package com.ghana.gwire.service.calc;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.calc.ValidationIssue;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.electrical.CircuitMaterializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates load estimation, diversity, cable sizing, BOQ aggregation, and
 * standards checks for a {@link Project}.
 *
 * <p><b>Important:</b> Diversity factors, assumed loads, cable current ratings, and
 * voltage-drop limits used here are <em>simplified engineering heuristics</em> aligned
 * with common BS/IEC and Ghana (L.I. 2008) practice for preliminary residential design.
 * They are <strong>not</strong> a substitute for manufacturer data, full installation-method
 * tables, or a Competent Electrical Wiring Professional (CEWP) design for real installations.
 *
 * <p>Each run records {@link AssumptionCodes} on the report and stamps
 * {@link com.ghana.gwire.domain.project.ProjectSettings#standardsEdition()}.
 */
public final class CalcEngine {

    public DesignReport calculate(Project project, ComponentLibraryService library) {
        Objects.requireNonNull(project, "project");
        AssumptionCollector assumptions = new AssumptionCollector();
        DesignReport report = new DesignReport();
        report.setProjectName(project.name());
        report.setCalculatedAt(Instant.now());
        report.setSupplyVoltageV(project.settings().nominalVoltageV());
        report.setSupplyTypeSummary(project.supplySummary() + " | " + project.settings().supplyType());
        report.setStandardsEdition(project.settings().standardsEdition());

        Map<String, ElectricalComponent> catalogue = loadCatalogue(library);
        List<ElectricalComponent> cables = catalogue.values().stream()
                .filter(c -> c.category() == ComponentCategory.CABLE)
                .toList();

        // Phase 14: prefer persistent circuits; materialize from geometry when empty
        CircuitMaterializer.ensureCircuits(project, catalogue, assumptions);
        List<CircuitLoad> circuits = buildLoads(project, catalogue, assumptions);

        double voltageV = project.settings().nominalVoltageV();
        if (voltageV <= 0) {
            voltageV = 230;
        }

        double totalConnected = circuits.stream().mapToDouble(CircuitLoad::connectedLoadW).sum();
        double totalAfterDiv = DiversityCalculator.applyToCircuits(circuits, voltageV, assumptions);
        sizeAndProtect(circuits, cables, voltageV, assumptions);

        double overallFactor = DiversityCalculator.overallInstallationFactor(circuits.size());
        double maxVdPct = circuits.stream().mapToDouble(CircuitLoad::voltageDropPercent).max().orElse(0);
        Map<String, DesignReport.CableBoqLine> boqAgg = aggregateCableBoq(circuits, cables);

        double totalDesignCurrentA = totalAfterDiv / voltageV;

        circuits.sort(Comparator
                .comparing((CircuitLoad c) -> c.kind().name())
                .thenComparing(CircuitLoad::name)
                .thenComparingDouble(CircuitLoad::connectedLoadW));

        report.setCircuits(circuits);
        report.setTotalConnectedLoadW(totalConnected);
        report.setTotalAfterDiversityW(totalAfterDiv);
        report.setTotalDesignCurrentA(totalDesignCurrentA);
        report.setDiversityApplied(overallFactor);
        report.setMaxVoltageDropPercent(maxVdPct);
        report.setCableBoq(new ArrayList<>(boqAgg.values()));

        List<ValidationIssue> issues = StandardsValidator.validate(
                project, circuits, totalDesignCurrentA, catalogue, assumptions);
        report.setIssues(issues);

        if (library == null && catalogue.isEmpty() && project.totalDeviceCount() > 0) {
            report.addIssue(ValidationIssue.of(
                    Severity.WARNING,
                    "NO_LIBRARY",
                    "Component library was null or empty - loads and cable sizes may be incomplete."
            ));
            assumptions.add(AssumptionCodes.NO_LIBRARY_WARNING);
        }

        CircuitMaterializer.applyCalcResults(project, circuits);
        report.setAssumptions(assumptions.sorted());
        return report;
    }

    /**
     * Build calc loads from persistent circuits (with device powers), or fall back to
     * {@link CircuitBuilder} when no circuits exist.
     */
    private static List<CircuitLoad> buildLoads(
            Project project,
            Map<String, ElectricalComponent> catalogue,
            AssumptionCollector assumptions
    ) {
        if (!project.circuits().isEmpty()) {
            List<CircuitLoad> loads = new ArrayList<>();
            Map<String, PlacedDevice> devices = indexDevices(project);
            double voltageV = project.settings().nominalVoltageV() > 0
                    ? project.settings().nominalVoltageV() : 230;
            for (Circuit c : project.circuits()) {
                CircuitLoad load = new CircuitLoad(c.id(), c.name(), c.kind(), c.roomId());
                double power = 0;
                for (String did : c.deviceIds()) {
                    load.addDeviceId(did);
                    PlacedDevice d = devices.get(did);
                    if (d != null) {
                        power += LoadTables.assumedPowerW(catalogue.get(d.componentId()), d, assumptions);
                    }
                }
                load.setConnectedLoadW(power);
                if (c.estimatedLengthM() > 0) {
                    load.setEstimatedLengthM(c.estimatedLengthM());
                } else {
                    // fallback length via estimator defaults
                    load.setEstimatedLengthM(CableLengthEstimator.defaultLengthM(c.kind()));
                    assumptions.add(AssumptionCodes.LENGTH_DEFAULT_FALLBACK);
                }
                if (power > 0) {
                    load.setDesignCurrentA(power / voltageV);
                }
                // Preserve CU / designer overrides from the persistent circuit model
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
        return CircuitBuilder.build(project, catalogue, assumptions);
    }

    private static Map<String, PlacedDevice> indexDevices(Project project) {
        Map<String, PlacedDevice> map = new LinkedHashMap<>();
        for (BuildingStorey s : project.storeys()) {
            for (PlacedDevice d : s.floorPlan().devices()) {
                map.put(d.id(), d);
            }
        }
        return map;
    }

    private static void sizeAndProtect(
            List<CircuitLoad> circuits,
            List<ElectricalComponent> cables,
            double voltageV,
            AssumptionCollector assumptions
    ) {
        for (CircuitLoad c : circuits) {
            if (c.designCurrentA() <= 0 && c.connectedLoadW() > 0) {
                c.setDesignCurrentA(c.connectedLoadW() / voltageV);
            }
            Optional<CableSizer.CableSelection> sized = CableSizer.size(
                    c.designCurrentA(),
                    c.estimatedLengthM(),
                    voltageV,
                    CableSizer.DEFAULT_MAX_VD_PERCENT,
                    cables,
                    c.kind(),
                    assumptions
            );
            if (sized.isPresent()) {
                CableSizer.CableSelection sel = sized.get();
                ElectricalComponent cable = sel.cable();
                c.setRecommendedCableId(cable.id());
                c.setRecommendedCableSize(
                        cable.standardSize() != null ? cable.standardSize() : cable.name());
                c.setVoltageDropV(sel.voltageDropV());
                c.setVoltageDropPercent(sel.voltageDropPercent());
            }
            double breaker = StandardsValidator.nextStandardBreakerA(c.designCurrentA());
            assumptions.add(AssumptionCodes.MCB_NEXT_STANDARD_RATING);
            if (c.kind() == CircuitKind.SOCKET
                    && c.designCurrentA() > StandardsValidator.SOCKET_HIGH_CURRENT_A
                    && breaker < 32) {
                breaker = 32;
                assumptions.add(AssumptionCodes.SOCKET_BREAKER_PREFER_32A_GT_20A);
            }
            // Prefer user/CU override if already set higher on load from circuit
            if (c.recommendedBreakerA() > breaker) {
                breaker = c.recommendedBreakerA();
            }
            c.setRecommendedBreakerA(breaker);
        }
    }

    private static Map<String, DesignReport.CableBoqLine> aggregateCableBoq(
            List<CircuitLoad> circuits,
            List<ElectricalComponent> cables
    ) {
        Map<String, DesignReport.CableBoqLine> boqAgg = new LinkedHashMap<>();
        Map<String, ElectricalComponent> byId = new LinkedHashMap<>();
        for (ElectricalComponent c : cables) {
            byId.put(c.id(), c);
        }
        for (CircuitLoad c : circuits) {
            if (c.recommendedCableId() == null) {
                continue;
            }
            ElectricalComponent cable = byId.get(c.recommendedCableId());
            String desc = cable != null
                    ? cable.name() + (cable.standardSize() != null ? " (" + cable.standardSize() + ")" : "")
                    : c.recommendedCableSize();
            boqAgg.merge(
                    c.recommendedCableId(),
                    new DesignReport.CableBoqLine(c.recommendedCableId(), c.estimatedLengthM(), desc),
                    (a, b) -> new DesignReport.CableBoqLine(
                            a.componentId(), a.lengthM() + b.lengthM(), a.description())
            );
        }
        return boqAgg;
    }

    /**
     * Convenience overload for unit tests with an in-memory catalogue (no DB).
     */
    public DesignReport calculate(Project project, List<ElectricalComponent> catalogueList) {
        Objects.requireNonNull(project, "project");
        Map<String, ElectricalComponent> map = new LinkedHashMap<>();
        if (catalogueList != null) {
            for (ElectricalComponent c : catalogueList) {
                if (c != null && c.id() != null) {
                    map.put(c.id(), c);
                }
            }
        }
        AssumptionCollector assumptions = new AssumptionCollector();
        DesignReport report = new DesignReport();
        report.setProjectName(project.name());
        report.setCalculatedAt(Instant.now());
        report.setSupplyVoltageV(project.settings().nominalVoltageV());
        report.setSupplyTypeSummary(project.supplySummary() + " | " + project.settings().supplyType());
        report.setStandardsEdition(project.settings().standardsEdition());

        List<ElectricalComponent> cables = map.values().stream()
                .filter(c -> c.category() == ComponentCategory.CABLE)
                .toList();

        CircuitMaterializer.ensureCircuits(project, map, assumptions);
        List<CircuitLoad> circuits = buildLoads(project, map, assumptions);
        double voltageV = project.settings().nominalVoltageV() > 0
                ? project.settings().nominalVoltageV() : 230;

        double totalConnected = circuits.stream().mapToDouble(CircuitLoad::connectedLoadW).sum();
        double totalAfterDiv = DiversityCalculator.applyToCircuits(circuits, voltageV, assumptions);
        sizeAndProtect(circuits, cables, voltageV, assumptions);
        double overallFactor = DiversityCalculator.overallInstallationFactor(circuits.size());
        double maxVdPct = circuits.stream().mapToDouble(CircuitLoad::voltageDropPercent).max().orElse(0);
        Map<String, DesignReport.CableBoqLine> boqAgg = aggregateCableBoq(circuits, cables);

        circuits.sort(Comparator
                .comparing((CircuitLoad c) -> c.kind().name())
                .thenComparing(CircuitLoad::name)
                .thenComparingDouble(CircuitLoad::connectedLoadW));

        double totalDesignCurrentA = totalAfterDiv / voltageV;
        report.setCircuits(circuits);
        report.setTotalConnectedLoadW(totalConnected);
        report.setTotalAfterDiversityW(totalAfterDiv);
        report.setTotalDesignCurrentA(totalDesignCurrentA);
        report.setDiversityApplied(overallFactor);
        report.setMaxVoltageDropPercent(maxVdPct);
        report.setCableBoq(new ArrayList<>(boqAgg.values()));
        report.setIssues(StandardsValidator.validate(project, circuits, totalDesignCurrentA, map, assumptions));
        if (map.isEmpty() && project.totalDeviceCount() > 0) {
            assumptions.add(AssumptionCodes.NO_LIBRARY_WARNING);
        }
        CircuitMaterializer.applyCalcResults(project, circuits);
        report.setAssumptions(assumptions.sorted());
        return report;
    }

    private static Map<String, ElectricalComponent> loadCatalogue(ComponentLibraryService library) {
        Map<String, ElectricalComponent> map = new LinkedHashMap<>();
        if (library == null) {
            return map;
        }
        try {
            for (ElectricalComponent c : library.listAll()) {
                if (c != null && c.id() != null) {
                    map.put(c.id(), c);
                }
            }
        } catch (RuntimeException ex) {
            // Null-safe: empty catalogue
        }
        return map;
    }
}
