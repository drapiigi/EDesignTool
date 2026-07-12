package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;

import java.util.List;
import java.util.Objects;

/**
 * Simplified residential diversity factors for Ghana / common BS practice.
 *
 * <p><b>Illustrative only</b> — not a substitute for full L.I. 2008 / BS 7671 Appendix
 * diversity tables. A Competent Electrical Wiring Professional (CEWP) must refine for
 * real installations.
 */
public final class DiversityCalculator {

    /** Lighting: full load on first 1000 W, then remainder at this factor. */
    public static final double LIGHTING_REMAINDER_FACTOR = 0.5;
    public static final double LIGHTING_FULL_BAND_W = 1000.0;

    /** Domestic socket circuits with multiple outlets (ring/radial average demand). */
    public static final double SOCKET_MULTI_FACTOR = 0.4;

    /** Cooker: base current (A) plus remainder of rated current at this factor (simplified BS). */
    public static final double COOKER_BASE_A = 10.0;
    public static final double COOKER_REMAINDER_FACTOR = 0.3;

    /** Overall installation diversity when more than this many final circuits. */
    public static final int OVERALL_CIRCUIT_THRESHOLD = 3;
    public static final double OVERALL_FACTOR = 0.9;

    private DiversityCalculator() {
    }

    /**
     * After-diversity load (W) for a single circuit given connected load and kind.
     *
     * @param kind              circuit kind
     * @param connectedLoadW    sum of assumed device powers
     * @param outletCount       number of socket outlets (used for SOCKET factor); ignored otherwise
     * @param supplyVoltageV    nominal voltage for cooker current conversion
     */
    public static double afterDiversityLoadW(
            CircuitKind kind,
            double connectedLoadW,
            int outletCount,
            double supplyVoltageV
    ) {
        if (connectedLoadW <= 0) {
            return 0;
        }
        CircuitKind k = kind == null ? CircuitKind.OTHER : kind;
        double v = supplyVoltageV > 0 ? supplyVoltageV : 230.0;

        return switch (k) {
            case LIGHTING -> lightingAfterDiversityW(connectedLoadW);
            case SOCKET -> socketAfterDiversityW(connectedLoadW, outletCount);
            case COOKER -> cookerAfterDiversityW(connectedLoadW, v);
            case WATER_HEATER, AC_OR_SPECIAL -> connectedLoadW; // fixed appliances: 100%
            case DISTRIBUTION, OTHER -> connectedLoadW;
        };
    }

    /**
     * Lighting: 100% of first 1000 W + 50% of remainder.
     */
    public static double lightingAfterDiversityW(double connectedLoadW) {
        if (connectedLoadW <= 0) {
            return 0;
        }
        if (connectedLoadW <= LIGHTING_FULL_BAND_W) {
            return connectedLoadW;
        }
        return LIGHTING_FULL_BAND_W + LIGHTING_REMAINDER_FACTOR * (connectedLoadW - LIGHTING_FULL_BAND_W);
    }

    /**
     * Socket circuits: factor 1.0 for a single outlet; 0.4 when multiple outlets
     * (simplified domestic ring/radial demand).
     */
    public static double socketAfterDiversityW(double connectedLoadW, int outletCount) {
        if (connectedLoadW <= 0) {
            return 0;
        }
        double factor = outletCount <= 1 ? 1.0 : SOCKET_MULTI_FACTOR;
        return connectedLoadW * factor;
    }

    /**
     * Simplified BS cooker diversity: 10 A + 30% of remainder of rated current,
     * converted back to watts at {@code supplyVoltageV}.
     * Falls back to 0.8 × connected if rated current is negligible.
     */
    public static double cookerAfterDiversityW(double connectedLoadW, double supplyVoltageV) {
        if (connectedLoadW <= 0) {
            return 0;
        }
        double v = supplyVoltageV > 0 ? supplyVoltageV : 230.0;
        double ratedCurrentA = connectedLoadW / v;
        if (ratedCurrentA <= COOKER_BASE_A) {
            return connectedLoadW * 0.8;
        }
        double diversifiedA = COOKER_BASE_A + COOKER_REMAINDER_FACTOR * (ratedCurrentA - COOKER_BASE_A);
        return diversifiedA * v;
    }

    /**
     * Diversity factor (after / connected) for the circuit; 1.0 if connected is zero.
     */
    public static double diversityFactor(
            CircuitKind kind,
            double connectedLoadW,
            int outletCount,
            double supplyVoltageV
    ) {
        if (connectedLoadW <= 0) {
            return 1.0;
        }
        double after = afterDiversityLoadW(kind, connectedLoadW, outletCount, supplyVoltageV);
        return after / connectedLoadW;
    }

    /**
     * Applies circuit-level diversity to each {@link CircuitLoad} (mutates list items)
     * and returns overall after-diversity total (W) with optional whole-installation factor.
     *
     * @param circuits       circuits with {@code connectedLoadW} already set
     * @param supplyVoltageV nominal voltage
     * @return overall after-diversity load in watts (after whole-installation factor)
     */
    public static double applyToCircuits(List<CircuitLoad> circuits, double supplyVoltageV) {
        Objects.requireNonNull(circuits, "circuits");
        double v = supplyVoltageV > 0 ? supplyVoltageV : 230.0;
        double sumAfter = 0;

        for (CircuitLoad c : circuits) {
            int outlets = c.kind() == CircuitKind.SOCKET ? Math.max(1, c.deviceIds().size()) : 0;
            // Switches attached to lighting don't count as outlets
            double afterW = afterDiversityLoadW(c.kind(), c.connectedLoadW(), outlets, v);
            double factor = c.connectedLoadW() > 0 ? afterW / c.connectedLoadW() : 1.0;
            c.setDiversityFactor(factor);
            c.setAfterDiversityLoadW(afterW);
            c.setAfterDiversityCurrentA(v > 0 ? afterW / v : 0);
            // Design current for protective device / cable: use after-diversity for sockets/lighting,
            // full connected for fixed high-load appliances is often preferred — Phase 4 uses after-diversity
            // for demand totals; design current for sizing uses max(connected/V, after/V) for fixed loads
            // and after-diversity current for diversified circuits.
            if (c.kind() == CircuitKind.COOKER
                    || c.kind() == CircuitKind.WATER_HEATER
                    || c.kind() == CircuitKind.AC_OR_SPECIAL) {
                // Protective device sized to connected; diversity only for main demand total
                double connectedA = c.connectedLoadW() / v;
                c.setDesignCurrentA(connectedA);
            } else {
                c.setDesignCurrentA(c.afterDiversityCurrentA());
            }
            sumAfter += afterW;
        }

        double overallFactor = overallInstallationFactor(circuits.size());
        return sumAfter * overallFactor;
    }

    /**
     * Whole-installation factor: 0.9 when more than 3 final circuits; else 1.0.
     */
    public static double overallInstallationFactor(int circuitCount) {
        return circuitCount > OVERALL_CIRCUIT_THRESHOLD ? OVERALL_FACTOR : 1.0;
    }
}
