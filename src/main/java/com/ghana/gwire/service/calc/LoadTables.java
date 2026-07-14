package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;

/**
 * Default assumed connected loads (W) for residential Ghana / BS-style demand estimation
 * when the catalogue entry has no {@code powerW}.
 *
 * <p>Socket demand figures are average contributions for diversity later — not continuous
 * full-load ratings of every outlet simultaneously.
 */
public final class LoadTables {

    /** Assumed average demand contribution per single 13A socket outlet (W). */
    public static final double SOCKET_13A_ASSUMED_W = 180.0;

    /** Default LED lighting when catalogue has no powerW (W). */
    public static final double LED_DEFAULT_W = 12.0;

    /** Default fluorescent lighting when catalogue has no powerW (W). */
    public static final double FLUORESCENT_DEFAULT_W = 36.0;

    /** Assumed cooker / 45A DP connected load (W). */
    public static final double COOKER_DEFAULT_W = 7000.0;

    /** Assumed water heater connected load (W). */
    public static final double WATER_HEATER_DEFAULT_W = 3000.0;

    /** Assumed air-conditioner connected load (W). */
    public static final double AC_DEFAULT_W = 2500.0;

    /** Fallback for unknown fixed loads (W). */
    public static final double UNKNOWN_DEFAULT_W = 100.0;

    private LoadTables() {
    }

    /**
     * Assumed connected power for a placed device.
     * Prefer catalogue {@code powerW}; otherwise use residential heuristics from name/category.
     *
     * @param component catalogue entry (may be null)
     * @param device    placed instance (may be null; used for name override only)
     * @return assumed power in watts (≥ 0)
     */
    public static double assumedPowerW(ElectricalComponent component, PlacedDevice device) {
        return assumedPowerW(component, device, null);
    }

    public static double assumedPowerW(
            ElectricalComponent component,
            PlacedDevice device,
            AssumptionCollector assumptions
    ) {
        if (component != null && component.powerW() != null && component.powerW() > 0) {
            add(assumptions, AssumptionCodes.LOAD_CATALOGUE_POWER_W);
            return component.powerW();
        }

        String name = combinedName(component, device);
        String lower = name.toLowerCase();
        ComponentCategory category = component == null ? null : component.category();
        String symbol = component == null ? "" : nullToEmpty(component.symbolKey()).toLowerCase();
        String id = component == null ? "" : nullToEmpty(component.id()).toLowerCase();
        Double ratingA = component == null ? null : component.currentRatingA();

        // Cooker control / 45A DP
        if (lower.contains("cooker")
                || id.contains("45a")
                || (ratingA != null && ratingA >= 45 && category == ComponentCategory.SWITCH)
                || symbol.contains("45a")) {
            add(assumptions, AssumptionCodes.COOKER_DEFAULT_7000W);
            return COOKER_DEFAULT_W;
        }

        // Water heater
        if (lower.contains("heater") || lower.contains("geyser") || lower.contains("immersion")) {
            add(assumptions, AssumptionCodes.WATER_HEATER_DEFAULT_3000W);
            return WATER_HEATER_DEFAULT_W;
        }

        // Air conditioning
        if (lower.contains("air condition")
                || lower.contains("air-con")
                || lower.contains("aircon")
                || containsAcToken(lower)
                || symbol.contains("ac_")
                || id.contains("-ac")) {
            add(assumptions, AssumptionCodes.AC_DEFAULT_2500W);
            return AC_DEFAULT_W;
        }

        if (category == ComponentCategory.LIGHTING) {
            if (lower.contains("fluor") || symbol.contains("fluor")) {
                add(assumptions, AssumptionCodes.FLUORESCENT_DEFAULT_36W);
                return FLUORESCENT_DEFAULT_W;
            }
            add(assumptions, AssumptionCodes.LED_DEFAULT_12W);
            return LED_DEFAULT_W;
        }

        if (category == ComponentCategory.SOCKET) {
            add(assumptions, AssumptionCodes.SOCKET_ASSUMED_180W);
            if (lower.contains("twin")
                    || lower.contains("2-gang")
                    || lower.contains("2 gang")
                    || lower.contains("2g")
                    || symbol.contains("2g")
                    || symbol.contains("twin")
                    || id.contains("2g")) {
                return SOCKET_13A_ASSUMED_W * 2;
            }
            return SOCKET_13A_ASSUMED_W;
        }

        if (category == ComponentCategory.SWITCH) {
            return 0.0;
        }

        if (category == ComponentCategory.CABLE
                || category == ComponentCategory.CIRCUIT_BREAKER
                || category == ComponentCategory.DISTRIBUTION_BOARD
                || category == ComponentCategory.PROTECTION
                || category == ComponentCategory.EARTHING
                || category == ComponentCategory.CONDUIT
                || category == ComponentCategory.JUNCTION
                || category == ComponentCategory.ISOLATOR) {
            return 0.0;
        }

        add(assumptions, AssumptionCodes.UNKNOWN_LOAD_100W);
        return UNKNOWN_DEFAULT_W;
    }

    private static void add(AssumptionCollector assumptions, String code) {
        if (assumptions != null) {
            assumptions.add(code);
        }
    }

    private static boolean containsAcToken(String lower) {
        // Match " ac " / leading / trailing "ac" as appliance, avoid matching "back" etc.
        if (lower.equals("ac") || lower.startsWith("ac ") || lower.endsWith(" ac") || lower.contains(" ac ")) {
            return true;
        }
        return lower.matches(".*\\bac\\b.*");
    }

    private static String combinedName(ElectricalComponent component, PlacedDevice device) {
        StringBuilder sb = new StringBuilder();
        if (component != null) {
            sb.append(nullToEmpty(component.name())).append(' ');
            sb.append(nullToEmpty(component.description())).append(' ');
            sb.append(nullToEmpty(component.id())).append(' ');
            sb.append(nullToEmpty(component.standardSize()));
        }
        if (device != null) {
            sb.append(' ').append(nullToEmpty(device.nameOverride()));
            sb.append(' ').append(nullToEmpty(device.displayName()));
            sb.append(' ').append(nullToEmpty(device.symbolKey()));
        }
        return sb.toString().trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
