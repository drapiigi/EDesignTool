package com.ghana.gwire.domain.electrical;

import com.ghana.gwire.domain.components.ComponentCategory;

/**
 * Default mounting heights above finished floor (mm) by component category.
 */
public final class MountingHeights {

    public static final double SOCKET_MM = 300;
    public static final double SWITCH_MM = 1200;
    public static final double LIGHT_MM = 2400;
    public static final double DB_MM = 1500;
    public static final double DEFAULT_MM = 0;

    private MountingHeights() {
    }

    public static double defaultFor(ComponentCategory category, String symbolKey) {
        if (category == null) {
            return defaultForSymbol(symbolKey);
        }
        return switch (category) {
            case SOCKET -> SOCKET_MM;
            case SWITCH -> SWITCH_MM;
            case LIGHTING -> LIGHT_MM;
            case DISTRIBUTION_BOARD -> DB_MM;
            default -> defaultForSymbol(symbolKey);
        };
    }

    private static double defaultForSymbol(String symbolKey) {
        if (symbolKey == null) {
            return DEFAULT_MM;
        }
        String k = symbolKey.toLowerCase();
        if (k.startsWith("socket")) {
            return SOCKET_MM;
        }
        if (k.startsWith("switch")) {
            return SWITCH_MM;
        }
        if (k.startsWith("light")) {
            return LIGHT_MM;
        }
        if (k.startsWith("db")) {
            return DB_MM;
        }
        return DEFAULT_MM;
    }
}
