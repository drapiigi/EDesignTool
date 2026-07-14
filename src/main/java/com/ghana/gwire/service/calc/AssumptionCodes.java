package com.ghana.gwire.service.calc;

/**
 * Stable assumption codes recorded on each {@link com.ghana.gwire.domain.calc.DesignReport}.
 * Documented in {@code docs/calc/FORMULAS.md}.
 */
public final class AssumptionCodes {

    public static final String LOAD_CATALOGUE_POWER_W = "LOAD_CATALOGUE_POWER_W";
    public static final String SOCKET_ASSUMED_180W = "SOCKET_ASSUMED_180W";
    public static final String LED_DEFAULT_12W = "LED_DEFAULT_12W";
    public static final String FLUORESCENT_DEFAULT_36W = "FLUORESCENT_DEFAULT_36W";
    public static final String COOKER_DEFAULT_7000W = "COOKER_DEFAULT_7000W";
    public static final String WATER_HEATER_DEFAULT_3000W = "WATER_HEATER_DEFAULT_3000W";
    public static final String AC_DEFAULT_2500W = "AC_DEFAULT_2500W";
    public static final String UNKNOWN_LOAD_100W = "UNKNOWN_LOAD_100W";

    public static final String DIVERSITY_LIGHTING_1000_THEN_0_5 = "DIVERSITY_LIGHTING_1000_THEN_0_5";
    public static final String DIVERSITY_SOCKET_MULTI_0_4 = "DIVERSITY_SOCKET_MULTI_0_4";
    public static final String DIVERSITY_COOKER_BASE_10A_PLUS_0_3 = "DIVERSITY_COOKER_BASE_10A_PLUS_0_3";
    public static final String DIVERSITY_OVERALL_0_9_GT_3_CIRCUITS = "DIVERSITY_OVERALL_0_9_GT_3_CIRCUITS";

    public static final String CABLE_VD_MAX_5_PERCENT = "CABLE_VD_MAX_5_PERCENT";
    public static final String CABLE_SIZE_FROM_CATALOGUE = "CABLE_SIZE_FROM_CATALOGUE";
    public static final String MCB_NEXT_STANDARD_RATING = "MCB_NEXT_STANDARD_RATING";
    public static final String SOCKET_BREAKER_PREFER_32A_GT_20A = "SOCKET_BREAKER_PREFER_32A_GT_20A";
    public static final String STANDARDS_HEURISTIC_LI2008 = "STANDARDS_HEURISTIC_LI2008";
    public static final String LENGTH_FROM_DB_GEOMETRY = "LENGTH_FROM_DB_GEOMETRY";
    public static final String LENGTH_DEFAULT_FALLBACK = "LENGTH_DEFAULT_FALLBACK";
    public static final String NO_LIBRARY_WARNING = "NO_LIBRARY_WARNING";

    public static final String DEFAULT_STANDARDS_EDITION = "L.I. 2008 practice tables v2026.1";

    private AssumptionCodes() {
    }
}
