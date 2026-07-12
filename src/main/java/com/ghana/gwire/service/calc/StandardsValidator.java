package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.calc.ValidationIssue;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.project.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Illustrative design checks aligned with common BS/IEC practice and Ghana
 * Electrical Wiring Regulations 2011 (L.I. 2008) good practice.
 *
 * <p><b>Important:</b> Thresholds and messages are simplified engineering heuristics
 * for educational / preliminary design in GhanaWire AI. They are <em>not</em> a full
 * compliance engine and must be refined by a Competent Electrical Wiring Professional
 * (CEWP) for real installations.
 */
public final class StandardsValidator {

    /** Standard domestic MCB ratings (A) used for next-up recommendations. */
    public static final int[] STANDARD_MCB_A = {6, 10, 16, 20, 32, 40, 63};

    public static final double MAIN_WARNING_A = 40.0;
    public static final double MAIN_DEFAULT_INCOMER_A = 60.0;
    public static final double MAIN_ERROR_A = 100.0;
    public static final double MAX_VD_PERCENT = 5.0;
    public static final double SOCKET_HIGH_CURRENT_A = 20.0;
    public static final double LIGHTING_MAX_A = 16.0;

    private StandardsValidator() {
    }

    /**
     * Run all Phase 4 validation checks.
     *
     * @param project              design project
     * @param circuits             calculated circuits
     * @param totalDesignCurrentA  overall after-diversity design current (A)
     * @param catalogue            component catalogue map
     */
    public static List<ValidationIssue> validate(
            Project project,
            List<CircuitLoad> circuits,
            double totalDesignCurrentA,
            Map<String, ElectricalComponent> catalogue
    ) {
        Objects.requireNonNull(project, "project");
        List<ValidationIssue> issues = new ArrayList<>();
        List<CircuitLoad> circs = circuits == null ? List.of() : circuits;
        Map<String, ElectricalComponent> cat = catalogue == null ? Map.of() : catalogue;
        FloorPlan plan = project.floorPlan();
        List<PlacedDevice> devices = plan.devices();

        validateMainIncomer(totalDesignCurrentA, issues);
        validateCircuits(circs, issues);
        validateProtectionAndEarth(devices, cat, issues);
        validateDistributionBoard(devices, cat, issues);
        validateCookerIsolation(circs, devices, cat, issues);

        return issues;
    }

    private static void validateMainIncomer(double totalDesignCurrentA, List<ValidationIssue> issues) {
        if (totalDesignCurrentA > MAIN_ERROR_A) {
            issues.add(ValidationIssue.of(
                    Severity.ERROR,
                    "OVERLOAD_MAIN",
                    ("Total after-diversity design current %.1f A exceeds %.0f A residential default "
                            + "(L.I. 2008 / supply capacity practice — upgrade supply or reduce load).")
                            .formatted(totalDesignCurrentA, MAIN_ERROR_A)
            ));
        } else if (totalDesignCurrentA > MAIN_DEFAULT_INCOMER_A) {
            issues.add(ValidationIssue.of(
                    Severity.ERROR,
                    "OVERLOAD_MAIN",
                    ("Total after-diversity design current %.1f A exceeds assumed single-phase incomer "
                            + "%.0f A — consider supply upgrade or split load (illustrative L.I. 2008 practice).")
                            .formatted(totalDesignCurrentA, MAIN_DEFAULT_INCOMER_A)
            ));
        } else if (totalDesignCurrentA > MAIN_WARNING_A) {
            issues.add(ValidationIssue.of(
                    Severity.WARNING,
                    "OVERLOAD_MAIN",
                    ("Total after-diversity design current %.1f A exceeds %.0f A — verify incomer and "
                            + "diversity with CEWP (illustrative threshold).")
                            .formatted(totalDesignCurrentA, MAIN_WARNING_A)
            ));
        }
    }

    private static void validateCircuits(List<CircuitLoad> circuits, List<ValidationIssue> issues) {
        for (CircuitLoad c : circuits) {
            // Voltage drop
            if (c.voltageDropPercent() > MAX_VD_PERCENT) {
                issues.add(ValidationIssue.of(
                        Severity.WARNING,
                        "VD_EXCEEDED",
                        ("Circuit '%s' estimated voltage drop %.2f%% exceeds %.0f%% limit "
                                + "(BS/IEC final-circuit practice). Consider larger CSA or shorter run.")
                                .formatted(c.name(), c.voltageDropPercent(), MAX_VD_PERCENT),
                        null,
                        c.roomId()
                ));
            }

            // Socket high design current
            if (c.kind() == CircuitKind.SOCKET && c.designCurrentA() > SOCKET_HIGH_CURRENT_A) {
                issues.add(ValidationIssue.of(
                        Severity.WARNING,
                        "SOCKET_HIGH_CURRENT",
                        ("Socket circuit '%s' design current %.1f A > 20 A — recommend 32 A breaker "
                                + "and adequately rated cable (ring/radial practice).")
                                .formatted(c.name(), c.designCurrentA()),
                        null,
                        c.roomId()
                ));
            }

            // Lighting overload → split
            if (c.kind() == CircuitKind.LIGHTING && c.designCurrentA() > LIGHTING_MAX_A) {
                issues.add(ValidationIssue.of(
                        Severity.ERROR,
                        "LIGHTING_SPLIT",
                        ("Lighting circuit '%s' design current %.1f A exceeds 16 A — split into "
                                + "additional lighting circuits (good practice / L.I. 2008).")
                                .formatted(c.name(), c.designCurrentA()),
                        null,
                        c.roomId()
                ));
            }

            // INFO: recommended MCB
            double breaker = c.recommendedBreakerA() > 0
                    ? c.recommendedBreakerA()
                    : nextStandardBreakerA(c.designCurrentA());
            if (c.designCurrentA() > 0 || c.connectedLoadW() > 0) {
                issues.add(ValidationIssue.of(
                        Severity.INFO,
                        "MCB_RECOMMEND",
                        ("Circuit '%s': recommended MCB rating %s A (next standard 6/10/16/20/32/40/63).")
                                .formatted(c.name(), formatAmp(breaker)),
                        null,
                        c.roomId()
                ));
            }
        }
    }

    private static void validateProtectionAndEarth(
            List<PlacedDevice> devices,
            Map<String, ElectricalComponent> cat,
            List<ValidationIssue> issues
    ) {
        boolean hasSockets = false;
        boolean hasRcd = false;
        boolean hasEarth = false;

        for (PlacedDevice d : devices) {
            ElectricalComponent c = cat.get(d.componentId());
            ComponentCategory category = c != null ? c.category() : inferCategory(d);
            String blob = blob(c, d);

            if (category == ComponentCategory.SOCKET) {
                hasSockets = true;
            }
            if (category == ComponentCategory.PROTECTION
                    || blob.contains("rcd")
                    || blob.contains("rccb")
                    || blob.contains("rcbo")) {
                // SPD alone is protection but not residual current — require RCD/RCCB/RCBO tokens
                if (blob.contains("rcd") || blob.contains("rccb") || blob.contains("rcbo")) {
                    hasRcd = true;
                } else if (c != null && c.category() == ComponentCategory.PROTECTION
                        && (nullToEmpty(c.name()).toLowerCase().contains("residual")
                        || nullToEmpty(c.id()).toUpperCase().contains("RCCB")
                        || nullToEmpty(c.id()).toUpperCase().contains("RCBO")
                        || nullToEmpty(c.id()).toUpperCase().contains("RCD"))) {
                    hasRcd = true;
                }
            }
            if (category == ComponentCategory.EARTHING
                    || blob.contains("earth")
                    || blob.contains("earthing")) {
                hasEarth = true;
            }
        }

        if (hasSockets && !hasRcd) {
            issues.add(ValidationIssue.of(
                    Severity.WARNING,
                    "NO_RCD",
                    "Socket outlets present but no RCD/RCCB/RCBO placed — residual current protection "
                            + "is required practice under L.I. 2008 / BS 7671 for socket circuits."
            ));
        }
        if (!hasEarth && !devices.isEmpty()) {
            issues.add(ValidationIssue.of(
                    Severity.WARNING,
                    "NO_EARTH",
                    "No earthing component placed — provide earth electrode / CPC bonding per "
                            + "L.I. 2008 earthing requirements."
            ));
        }
    }

    private static void validateDistributionBoard(
            List<PlacedDevice> devices,
            Map<String, ElectricalComponent> cat,
            List<ValidationIssue> issues
    ) {
        int loadDevices = 0;
        boolean hasDb = false;
        for (PlacedDevice d : devices) {
            ElectricalComponent c = cat.get(d.componentId());
            ComponentCategory category = c != null ? c.category() : inferCategory(d);
            String symbol = nullToEmpty(d.symbolKey()).toLowerCase();
            String cid = nullToEmpty(d.componentId()).toLowerCase();
            if (category == ComponentCategory.DISTRIBUTION_BOARD
                    || symbol.startsWith("db_")
                    || cid.startsWith("db-")) {
                hasDb = true;
            }
            if (category == ComponentCategory.LIGHTING
                    || category == ComponentCategory.SOCKET
                    || category == ComponentCategory.SWITCH) {
                // switches count lightly; task says >3 load devices
                if (category != ComponentCategory.SWITCH) {
                    loadDevices++;
                }
            } else if (category == ComponentCategory.OTHER || category == ComponentCategory.ISOLATOR) {
                // ignore
            }
        }
        // Also count special appliance devices already as load via lighting/socket; cookers are SWITCH category
        for (PlacedDevice d : devices) {
            ElectricalComponent c = cat.get(d.componentId());
            if (CircuitBuilder.classifySpecial(c, d) != null) {
                loadDevices++;
            }
        }

        if (!hasDb && loadDevices > 3) {
            issues.add(ValidationIssue.of(
                    Severity.WARNING,
                    "NO_DB",
                    ("No distribution board / consumer unit placed while %d load devices are present "
                            + "(>3) — provide a DB for circuit protection (L.I. 2008 practice).")
                            .formatted(loadDevices)
            ));
        }
    }

    private static void validateCookerIsolation(
            List<CircuitLoad> circuits,
            List<PlacedDevice> devices,
            Map<String, ElectricalComponent> cat,
            List<ValidationIssue> issues
    ) {
        boolean hasCookerCircuit = circuits.stream().anyMatch(c -> c.kind() == CircuitKind.COOKER);
        if (!hasCookerCircuit) {
            return;
        }
        boolean hasIsolation = false;
        for (PlacedDevice d : devices) {
            ElectricalComponent c = cat.get(d.componentId());
            String blob = blob(c, d);
            Double rating = c == null ? null : c.currentRatingA();
            if (blob.contains("45a") || blob.contains("cooker")) {
                hasIsolation = true;
                break;
            }
            if (rating != null && rating >= 32
                    && (c.category() == ComponentCategory.SWITCH
                    || c.category() == ComponentCategory.ISOLATOR
                    || blob.contains("dp"))) {
                hasIsolation = true;
                break;
            }
        }
        if (!hasIsolation) {
            issues.add(ValidationIssue.of(
                    Severity.WARNING,
                    "COOKER_ISOLATION",
                    "Cooker circuit present without a 45 A or 32 A+ double-pole isolation device "
                            + "in the project (L.I. 2008 fixed-appliance isolation practice)."
            ));
        }
    }

    /**
     * Next standard MCB rating (A) ≥ design current; caps at 63 A table.
     */
    public static double nextStandardBreakerA(double designCurrentA) {
        if (designCurrentA <= 0) {
            return STANDARD_MCB_A[0];
        }
        for (int a : STANDARD_MCB_A) {
            if (a >= designCurrentA - 1e-9) {
                return a;
            }
        }
        return STANDARD_MCB_A[STANDARD_MCB_A.length - 1];
    }

    private static ComponentCategory inferCategory(PlacedDevice d) {
        String symbol = nullToEmpty(d.symbolKey()).toLowerCase();
        if (symbol.startsWith("socket") || symbol.contains("sock")) {
            return ComponentCategory.SOCKET;
        }
        if (symbol.startsWith("light")) {
            return ComponentCategory.LIGHTING;
        }
        if (symbol.startsWith("db_")) {
            return ComponentCategory.DISTRIBUTION_BOARD;
        }
        if (symbol.contains("earth")) {
            return ComponentCategory.EARTHING;
        }
        if (symbol.contains("rccb") || symbol.contains("rcbo") || symbol.contains("rcd")) {
            return ComponentCategory.PROTECTION;
        }
        return ComponentCategory.OTHER;
    }

    private static String blob(ElectricalComponent c, PlacedDevice d) {
        StringBuilder sb = new StringBuilder();
        if (c != null) {
            sb.append(nullToEmpty(c.id())).append(' ')
                    .append(nullToEmpty(c.name())).append(' ')
                    .append(nullToEmpty(c.symbolKey())).append(' ')
                    .append(nullToEmpty(c.description()));
        }
        if (d != null) {
            sb.append(' ').append(nullToEmpty(d.componentId()))
                    .append(' ').append(nullToEmpty(d.symbolKey()))
                    .append(' ').append(nullToEmpty(d.nameOverride()));
        }
        return sb.toString().toLowerCase();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String formatAmp(double a) {
        if (Math.abs(a - Math.rint(a)) < 1e-6) {
            return String.valueOf((int) Math.rint(a));
        }
        return String.format("%.1f", a);
    }
}
