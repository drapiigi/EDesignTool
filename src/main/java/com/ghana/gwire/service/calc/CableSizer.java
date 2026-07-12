package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects the smallest suitable cable CSA from the catalogue for a final circuit.
 *
 * <p>Uses catalogue {@code currentRatingA} and {@code voltageDropMvPerAm} with the
 * common approximation {@code Vd = (mV/A/m × I × L) / 1000}. Parameters are
 * illustrative BS/IEC practice and must be refined for installation method / grouping.
 */
public final class CableSizer {

    public static final double DEFAULT_MAX_VD_PERCENT = 5.0;
    public static final double CURRENT_SPARE_FACTOR = 1.0;

    /**
     * Result of a cable sizing calculation.
     *
     * @param cable               selected catalogue cable
     * @param voltageDropV        absolute voltage drop (V)
     * @param voltageDropPercent  voltage drop as % of supply
     */
    public record CableSelection(
            ElectricalComponent cable,
            double voltageDropV,
            double voltageDropPercent
    ) {
    }

    private CableSizer() {
    }

    /**
     * Voltage drop (V): {@code (mvPerAm * I * L) / 1000}.
     */
    public static double voltageDropV(double mvPerAm, double currentA, double lengthM) {
        return (mvPerAm * currentA * lengthM) / 1000.0;
    }

    public static double voltageDropPercent(double voltageDropV, double supplyVoltageV) {
        if (supplyVoltageV <= 0) {
            return 0;
        }
        return 100.0 * voltageDropV / supplyVoltageV;
    }

    /**
     * Pick smallest CSA cable meeting current capacity and voltage-drop limit.
     *
     * @param designCurrentA design current (A)
     * @param lengthM        estimated run length (m)
     * @param voltageV       supply voltage (V)
     * @param maxVdPercent   max allowed Vd % (default 5.0)
     * @param cables         catalogue cables (CABLE category preferred)
     * @param kind           circuit kind for twin&earth preference
     */
    public static Optional<CableSelection> size(
            double designCurrentA,
            double lengthM,
            double voltageV,
            double maxVdPercent,
            List<ElectricalComponent> cables,
            CircuitKind kind
    ) {
        if (cables == null || cables.isEmpty() || designCurrentA < 0 || lengthM < 0) {
            return Optional.empty();
        }
        double maxVd = maxVdPercent > 0 ? maxVdPercent : DEFAULT_MAX_VD_PERCENT;
        double v = voltageV > 0 ? voltageV : 230.0;
        double requiredA = designCurrentA * CURRENT_SPARE_FACTOR;
        double maxVdVolts = maxVd * v / 100.0;

        List<ElectricalComponent> candidates = cables.stream()
                .filter(Objects::nonNull)
                .filter(c -> c.category() == ComponentCategory.CABLE || c.crossSectionMm2() != null)
                .filter(c -> c.currentRatingA() != null && c.voltageDropMvPerAm() != null)
                .filter(c -> c.crossSectionMm2() != null)
                .filter(c -> c.currentRatingA() >= requiredA)
                .sorted(Comparator
                        .comparingDouble((ElectricalComponent c) -> c.crossSectionMm2())
                        .thenComparing(CableSizer::twinEarthPreferenceRank)
                        .thenComparing(ElectricalComponent::id))
                .toList();

        boolean preferTwin = kind == CircuitKind.LIGHTING || kind == CircuitKind.SOCKET;

        // First pass: among smallest CSA that pass Vd, prefer twin&earth if applicable
        Double bestCsa = null;
        CableSelection best = null;

        for (ElectricalComponent cable : candidates) {
            double vd = voltageDropV(cable.voltageDropMvPerAm(), designCurrentA, lengthM);
            if (vd > maxVdVolts + 1e-9) {
                continue;
            }
            double csa = cable.crossSectionMm2();
            if (bestCsa == null || csa < bestCsa - 1e-9) {
                bestCsa = csa;
                best = new CableSelection(cable, vd, voltageDropPercent(vd, v));
            } else if (bestCsa != null && Math.abs(csa - bestCsa) < 1e-9) {
                // Same CSA: prefer twin&earth for lighting/socket when available
                if (preferTwin && isTwinEarth(cable) && !isTwinEarth(best.cable())) {
                    best = new CableSelection(cable, vd, voltageDropPercent(vd, v));
                }
            }
        }

        if (best != null) {
            return Optional.of(best);
        }

        // Fallback: largest capacity cable even if Vd fails (caller can warn)
        return candidates.stream()
                .max(Comparator.comparingDouble(c -> c.crossSectionMm2()))
                .map(cable -> {
                    double vd = voltageDropV(cable.voltageDropMvPerAm(), designCurrentA, lengthM);
                    return new CableSelection(cable, vd, voltageDropPercent(vd, v));
                });
    }

    public static Optional<CableSelection> size(
            double designCurrentA,
            double lengthM,
            double voltageV,
            List<ElectricalComponent> cables,
            CircuitKind kind
    ) {
        return size(designCurrentA, lengthM, voltageV, DEFAULT_MAX_VD_PERCENT, cables, kind);
    }

    static boolean isTwinEarth(ElectricalComponent c) {
        if (c == null) {
            return false;
        }
        String id = c.id() == null ? "" : c.id().toUpperCase();
        String symbol = c.symbolKey() == null ? "" : c.symbolKey().toLowerCase();
        String name = c.name() == null ? "" : c.name().toLowerCase();
        return id.contains("TWIN")
                || symbol.contains("cable_twin")
                || name.contains("twin");
    }

    /** Lower rank = preferred when sorting same CSA. */
    private static int twinEarthPreferenceRank(ElectricalComponent c) {
        return isTwinEarth(c) ? 0 : 1;
    }
}
