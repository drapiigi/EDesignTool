package com.ghana.gwire.domain.calc;

/**
 * Classification of a final circuit for diversity and cable sizing heuristics.
 */
public enum CircuitKind {
    LIGHTING,
    SOCKET,
    COOKER,
    WATER_HEATER,
    AC_OR_SPECIAL,
    DISTRIBUTION,
    OTHER
}
