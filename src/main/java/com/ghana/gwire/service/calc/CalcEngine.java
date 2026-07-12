package com.ghana.gwire.service.calc;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.calc.ValidationIssue;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.project.Project;

import java.time.Instant;
import java.util.ArrayList;
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
 * <p>The engine stores nothing globally; callers may optionally persist the report via
 * {@link Project#setLastReport(DesignReport)}.
 */
public final class CalcEngine {

    public DesignReport calculate(Project project, ComponentLibraryService library) {
        Objects.requireNonNull(project, "project");
        DesignReport report = new DesignReport();
        report.setProjectName(project.name());
        report.setCalculatedAt(Instant.now());
        report.setSupplyVoltageV(project.settings().nominalVoltageV());
        report.setSupplyTypeSummary(project.supplySummary() + " · " + project.settings().supplyType());

        Map<String, ElectricalComponent> catalogue = loadCatalogue(library);
        List<ElectricalComponent> cables = catalogue.values().stream()
                .filter(c -> c.category() == ComponentCategory.CABLE)
                .toList();

        List<CircuitLoad> circuits = CircuitBuilder.build(project, catalogue);

        double voltageV = project.settings().nominalVoltageV();
        if (voltageV <= 0) {
            voltageV = 230;
        }

        // Diversity (mutates circuits; returns overall after-diversity W with whole-install factor)
        double totalConnected = circuits.stream().mapToDouble(CircuitLoad::connectedLoadW).sum();
        double totalAfterDiv = DiversityCalculator.applyToCircuits(circuits, voltageV);
        double overallFactor = DiversityCalculator.overallInstallationFactor(circuits.size());

        // Cable sizing + breaker recommendation
        double maxVdPct = 0;
        Map<String, DesignReport.CableBoqLine> boqAgg = new LinkedHashMap<>();

        for (CircuitLoad c : circuits) {
            // Design current already set by diversity apply; ensure non-zero lighting/socket use after-div I
            if (c.designCurrentA() <= 0 && c.connectedLoadW() > 0) {
                c.setDesignCurrentA(c.connectedLoadW() / voltageV);
            }

            Optional<CableSizer.CableSelection> sized = CableSizer.size(
                    c.designCurrentA(),
                    c.estimatedLengthM(),
                    voltageV,
                    CableSizer.DEFAULT_MAX_VD_PERCENT,
                    cables,
                    c.kind()
            );

            if (sized.isPresent()) {
                CableSizer.CableSelection sel = sized.get();
                ElectricalComponent cable = sel.cable();
                c.setRecommendedCableId(cable.id());
                c.setRecommendedCableSize(
                        cable.standardSize() != null ? cable.standardSize() : cable.name());
                c.setVoltageDropV(sel.voltageDropV());
                c.setVoltageDropPercent(sel.voltageDropPercent());

                boqAgg.merge(
                        cable.id(),
                        new DesignReport.CableBoqLine(
                                cable.id(),
                                c.estimatedLengthM(),
                                cable.name() + (cable.standardSize() != null ? " (" + cable.standardSize() + ")" : "")
                        ),
                        (a, b) -> new DesignReport.CableBoqLine(
                                a.componentId(),
                                a.lengthM() + b.lengthM(),
                                a.description()
                        )
                );
            }

            double breaker = StandardsValidator.nextStandardBreakerA(c.designCurrentA());
            // Socket circuits with design I > 20 A → prefer 32 A
            if (c.kind() == com.ghana.gwire.domain.calc.CircuitKind.SOCKET
                    && c.designCurrentA() > StandardsValidator.SOCKET_HIGH_CURRENT_A
                    && breaker < 32) {
                breaker = 32;
            }
            c.setRecommendedBreakerA(breaker);

            if (c.voltageDropPercent() > maxVdPct) {
                maxVdPct = c.voltageDropPercent();
            }
        }

        double totalDesignCurrentA = totalAfterDiv / voltageV;

        report.setCircuits(circuits);
        report.setTotalConnectedLoadW(totalConnected);
        report.setTotalAfterDiversityW(totalAfterDiv);
        report.setTotalDesignCurrentA(totalDesignCurrentA);
        report.setDiversityApplied(overallFactor);
        report.setMaxVoltageDropPercent(maxVdPct);
        report.setCableBoq(new ArrayList<>(boqAgg.values()));

        List<ValidationIssue> issues = StandardsValidator.validate(
                project, circuits, totalDesignCurrentA, catalogue);
        report.setIssues(issues);

        // Null/empty safety note
        if (library == null && catalogue.isEmpty() && !project.floorPlan().devices().isEmpty()) {
            report.addIssue(ValidationIssue.of(
                    Severity.WARNING,
                    "NO_LIBRARY",
                    "Component library was null or empty — loads and cable sizes may be incomplete."
            ));
        }

        return report;
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
        // Build a lightweight path without ComponentLibraryService
        DesignReport report = new DesignReport();
        report.setProjectName(project.name());
        report.setCalculatedAt(Instant.now());
        report.setSupplyVoltageV(project.settings().nominalVoltageV());
        report.setSupplyTypeSummary(project.supplySummary() + " · " + project.settings().supplyType());

        List<ElectricalComponent> cables = map.values().stream()
                .filter(c -> c.category() == ComponentCategory.CABLE)
                .toList();

        List<CircuitLoad> circuits = CircuitBuilder.build(project, map);
        double voltageV = project.settings().nominalVoltageV() > 0
                ? project.settings().nominalVoltageV() : 230;

        double totalConnected = circuits.stream().mapToDouble(CircuitLoad::connectedLoadW).sum();
        double totalAfterDiv = DiversityCalculator.applyToCircuits(circuits, voltageV);
        double overallFactor = DiversityCalculator.overallInstallationFactor(circuits.size());
        double maxVdPct = 0;
        Map<String, DesignReport.CableBoqLine> boqAgg = new LinkedHashMap<>();

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
                    c.kind()
            );
            if (sized.isPresent()) {
                CableSizer.CableSelection sel = sized.get();
                ElectricalComponent cable = sel.cable();
                c.setRecommendedCableId(cable.id());
                c.setRecommendedCableSize(
                        cable.standardSize() != null ? cable.standardSize() : cable.name());
                c.setVoltageDropV(sel.voltageDropV());
                c.setVoltageDropPercent(sel.voltageDropPercent());
                boqAgg.merge(
                        cable.id(),
                        new DesignReport.CableBoqLine(
                                cable.id(),
                                c.estimatedLengthM(),
                                cable.name() + (cable.standardSize() != null ? " (" + cable.standardSize() + ")" : "")
                        ),
                        (a, b) -> new DesignReport.CableBoqLine(
                                a.componentId(), a.lengthM() + b.lengthM(), a.description())
                );
            }
            double breaker = StandardsValidator.nextStandardBreakerA(c.designCurrentA());
            if (c.kind() == com.ghana.gwire.domain.calc.CircuitKind.SOCKET
                    && c.designCurrentA() > StandardsValidator.SOCKET_HIGH_CURRENT_A
                    && breaker < 32) {
                breaker = 32;
            }
            c.setRecommendedBreakerA(breaker);
            if (c.voltageDropPercent() > maxVdPct) {
                maxVdPct = c.voltageDropPercent();
            }
        }

        double totalDesignCurrentA = totalAfterDiv / voltageV;
        report.setCircuits(circuits);
        report.setTotalConnectedLoadW(totalConnected);
        report.setTotalAfterDiversityW(totalAfterDiv);
        report.setTotalDesignCurrentA(totalDesignCurrentA);
        report.setDiversityApplied(overallFactor);
        report.setMaxVoltageDropPercent(maxVdPct);
        report.setCableBoq(new ArrayList<>(boqAgg.values()));
        report.setIssues(StandardsValidator.validate(project, circuits, totalDesignCurrentA, map));
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
