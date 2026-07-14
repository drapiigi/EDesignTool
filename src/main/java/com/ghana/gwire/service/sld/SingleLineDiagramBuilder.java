package com.ghana.gwire.service.sld;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.calc.CalcEngine;

import java.util.Locale;
import java.util.Objects;

/**
 * Builds a simplified single-line diagram from project calc results (Phase 9).
 */
public final class SingleLineDiagramBuilder {

    private final CalcEngine calcEngine = new CalcEngine();

    public SingleLineDiagram build(Project project) {
        Objects.requireNonNull(project, "project");
        DesignReport report = project.lastReport();
        if (report == null) {
            try {
                report = calcEngine.calculate(project, LibraryBootstrap.get());
            } catch (Exception e) {
                report = calcEngine.calculate(project, (ComponentLibraryService) null);
            }
            project.setLastReport(report);
        }
        return build(project, report);
    }

    public SingleLineDiagram build(Project project, DesignReport report) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(report, "report");

        String supply = String.format(Locale.ROOT, "ECG supply %.0f V / %.0f Hz",
                project.settings().nominalVoltageV(),
                project.settings().frequencyHz());

        SingleLineDiagram.Node root = new SingleLineDiagram.Node(
                SingleLineDiagram.NodeKind.SUPPLY, "Utility supply", supply);

        var cu = project.consumerUnit();
        double incomer = cu != null ? cu.incomerA() : 60;
        String rcdDesc = cu != null ? cu.rcdDescription() : "RCCB 63 A 30 mA";
        String cuName = cu != null ? cu.name() : "Consumer unit";

        SingleLineDiagram.Node main = new SingleLineDiagram.Node(
                SingleLineDiagram.NodeKind.MAIN_SWITCH,
                "Main switch / isolator",
                String.format(Locale.ROOT, "Incomer %.0f A | Design I ~ %.1f A",
                        incomer, report.totalDesignCurrentA())
        );
        root.add(main);

        SingleLineDiagram.Node rcd = new SingleLineDiagram.Node(
                SingleLineDiagram.NodeKind.RCD,
                "RCCB / main RCD",
                rcdDesc
        );
        main.add(rcd);

        SingleLineDiagram.Node bus = new SingleLineDiagram.Node(
                SingleLineDiagram.NodeKind.BUSBAR,
                cuName + " busbar",
                (cu != null ? cu.ways() + " ways | " : "")
                        + report.circuits().size() + " final circuit(s)"
        );
        rcd.add(bus);

        // Prefer CU way order when available
        java.util.List<CircuitLoad> ordered = new java.util.ArrayList<>(report.circuits());
        if (cu != null) {
            java.util.Map<String, CircuitLoad> byId = new java.util.LinkedHashMap<>();
            for (CircuitLoad c : report.circuits()) {
                byId.put(c.id(), c);
            }
            ordered.clear();
            for (int i = 0; i < cu.ways(); i++) {
                String cid = cu.circuitIdAtWay(i);
                if (cid != null && byId.containsKey(cid)) {
                    ordered.add(byId.remove(cid));
                }
            }
            ordered.addAll(byId.values());
        }

        for (CircuitLoad c : ordered) {
            String mcbLabel = c.recommendedBreakerA() > 0
                    ? String.format(Locale.ROOT, "MCB %.0f A", c.recommendedBreakerA())
                    : "MCB";
            // Prefer persistent circuit name/breaker override
            var pc = project.findCircuit(c.id());
            if (pc.isPresent() && pc.get().breakerA() > 0) {
                mcbLabel = String.format(Locale.ROOT, "MCB %.0f A", pc.get().breakerA());
            }
            SingleLineDiagram.Node mcb = new SingleLineDiagram.Node(
                    SingleLineDiagram.NodeKind.MCB,
                    mcbLabel,
                    c.kind().name() + (pc.map(x -> x.wayNumber() > 0 ? " way " + x.wayNumber() : "").orElse(""))
            );
            String cable = c.recommendedCableSize() == null || c.recommendedCableSize().isBlank()
                    ? "cable TBD"
                    : c.recommendedCableSize();
            if (pc.isPresent() && !pc.get().cableSize().isBlank()) {
                cable = pc.get().cableSize();
            }
            String loadName = pc.map(com.ghana.gwire.domain.electrical.Circuit::name).orElse(c.name());
            SingleLineDiagram.Node load = new SingleLineDiagram.Node(
                    SingleLineDiagram.NodeKind.LOAD,
                    loadName,
                    String.format(Locale.ROOT, "%.0f W | %.1f A | %s | Vd %.2f%%",
                            c.connectedLoadW(), c.designCurrentA(), cable, c.voltageDropPercent())
            );
            mcb.add(load);
            bus.add(mcb);
        }

        SingleLineDiagram.Node earth = new SingleLineDiagram.Node(
                SingleLineDiagram.NodeKind.EARTH,
                "Earthing electrode / bonding",
                "TT/TN practice per L.I. 2008 context"
        );
        main.add(earth);

        String notes = "Schematic SLD for preliminary design only. Not a certified single-line drawing. "
                + "Verify with a CEWP. Storeys: " + project.storeys().size()
                + " | Devices: " + project.totalDeviceCount();

        return new SingleLineDiagram(
                "Single-line diagram - " + project.name(),
                root,
                notes
        );
    }
}
