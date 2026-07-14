package com.ghana.gwire.service.calc;

/**
 * Session-only calculation freshness for export gates (not persisted in {@code .gwire}).
 */
public enum CalcSessionState {
    /** Opened/created; never successfully calculated this session. */
    NONE,
    /** {@code lastReport} matches current model revision. */
    FRESH,
    /** Model changed since last report; report cleared. */
    DIRTY_CLEARED,
    /** Fresh report exists and contains ERROR severity issues. */
    ERRORS_PRESENT
}
