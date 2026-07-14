package com.ghana.gwire.service.cad;

import java.util.Locale;
import java.util.Optional;

/**
 * Parses MVP CAD command-line input (Phase 15): LINE, length, ORTHO, help.
 */
public final class CadCommandParser {

    public enum Kind {
        LINE,
        LENGTH,
        ORTHO_ON,
        ORTHO_OFF,
        OSNAP_ON,
        OSNAP_OFF,
        CANCEL,
        HELP,
        UNKNOWN
    }

    public record Result(Kind kind, double valueMm, String message) {
        public static Result of(Kind kind) {
            return new Result(kind, 0, "");
        }

        public static Result length(double mm) {
            return new Result(Kind.LENGTH, mm, "");
        }

        public static Result unknown(String msg) {
            return new Result(Kind.UNKNOWN, 0, msg == null ? "" : msg);
        }
    }

    private CadCommandParser() {
    }

    public static Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Result.of(Kind.HELP);
        }
        String s = raw.trim();
        String upper = s.toUpperCase(Locale.ROOT);

        if (upper.equals("?") || upper.equals("HELP") || upper.equals("H")) {
            return Result.of(Kind.HELP);
        }
        if (upper.equals("ESC") || upper.equals("CANCEL") || upper.equals("X")) {
            return Result.of(Kind.CANCEL);
        }
        if (upper.equals("LINE") || upper.equals("L")) {
            return Result.of(Kind.LINE);
        }
        if (upper.equals("ORTHO ON") || upper.equals("ORTHO")) {
            return Result.of(Kind.ORTHO_ON);
        }
        if (upper.equals("ORTHO OFF")) {
            return Result.of(Kind.ORTHO_OFF);
        }
        if (upper.equals("OSNAP ON") || upper.equals("OSNAP")) {
            return Result.of(Kind.OSNAP_ON);
        }
        if (upper.equals("OSNAP OFF")) {
            return Result.of(Kind.OSNAP_OFF);
        }

        Optional<Double> length = parseLengthMm(s);
        if (length.isPresent()) {
            return Result.length(length.get());
        }
        return Result.unknown("Unknown command: " + s + " (try LINE, 3500, 3.5m, ORTHO ON, HELP)");
    }

    /**
     * Accepts {@code 3500}, {@code 3500mm}, {@code 3.5m}, {@code 3,5 m}.
     */
    public static Optional<Double> parseLengthMm(String s) {
        if (s == null || s.isBlank()) {
            return Optional.empty();
        }
        String t = s.trim().toLowerCase(Locale.ROOT).replace(',', '.');
        boolean metres = false;
        if (t.endsWith("mm")) {
            t = t.substring(0, t.length() - 2).trim();
        } else if (t.endsWith("m")) {
            metres = true;
            t = t.substring(0, t.length() - 1).trim();
        }
        try {
            double v = Double.parseDouble(t);
            if (v <= 0) {
                return Optional.empty();
            }
            return Optional.of(metres ? v * 1000.0 : v);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
