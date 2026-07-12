package com.ghana.gwire.ui.symbols;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Draws IEC/BS-inspired electrical symbols on the floor-plan canvas.
 * Coordinates are screen pixels; {@code size} is the symbol diameter/width in px.
 */
public final class SymbolRenderer {

    private SymbolRenderer() {
    }

    public static void draw(
            GraphicsContext g,
            String symbolKey,
            double cx,
            double cy,
            double size,
            double rotationDeg,
            boolean selected
    ) {
        if (symbolKey == null) {
            symbolKey = "generic";
        }
        g.save();
        g.translate(cx, cy);
        g.rotate(rotationDeg);

        Color stroke = selected ? Color.web("#f0b429") : Color.web("#e8edf5");
        Color fill = selected ? Color.web("#f0b429", 0.25) : Color.web("#1a2030", 0.85);
        g.setStroke(stroke);
        g.setFill(fill);
        g.setLineWidth(selected ? 2.2 : 1.6);

        String key = symbolKey.toLowerCase();
        if (key.startsWith("socket")) {
            drawSocket(g, size, key.contains("2g"), key.contains("industrial"));
        } else if (key.startsWith("switch")) {
            drawSwitch(g, size, key.contains("dimmer"), key.contains("dp") || key.contains("20a") || key.contains("45a"));
        } else if (key.startsWith("light")) {
            drawLight(g, size, key);
        } else if (key.startsWith("mcb") || key.startsWith("rcbo")) {
            drawBreaker(g, size, key.contains("3p") ? "3P" : "1P");
        } else if (key.startsWith("rccb")) {
            drawBreaker(g, size, "RCD");
        } else if (key.startsWith("db")) {
            drawDb(g, size);
        } else if (key.startsWith("cable")) {
            drawCable(g, size);
        } else if (key.startsWith("earth")) {
            drawEarth(g, size);
        } else if (key.startsWith("conduit") || key.startsWith("trunking")) {
            drawConduit(g, size);
        } else if (key.startsWith("junction")) {
            drawJunction(g, size);
        } else if (key.startsWith("isolator") || key.startsWith("changeover")) {
            drawIsolator(g, size, key.startsWith("changeover"));
        } else if (key.startsWith("meter")) {
            drawMeter(g, size);
        } else if (key.startsWith("spd")) {
            drawSpd(g, size);
        } else {
            drawGeneric(g, size, shortLabel(key));
        }

        g.restore();
    }

    private static void drawSocket(GraphicsContext g, double size, boolean twin, boolean industrial) {
        double r = size * 0.45;
        g.fillOval(-r, -r, r * 2, r * 2);
        g.strokeOval(-r, -r, r * 2, r * 2);
        if (industrial) {
            g.strokeOval(-r * 0.55, -r * 0.55, r * 1.1, r * 1.1);
        }
        // BS 1363 style pin marks
        double pin = r * 0.18;
        g.setFill(g.getStroke());
        g.fillOval(-pin * 2.2, -pin * 0.5, pin, pin);
        g.fillOval(pin * 1.2, -pin * 0.5, pin, pin);
        g.fillOval(-pin * 0.5, pin * 1.2, pin, pin);
        if (twin) {
            g.strokeOval(r * 0.85, -r * 0.35, r * 0.7, r * 0.7);
        }
        label(g, twin ? "2×13A" : (industrial ? "IEC" : "13A"), 0, r + 10, size);
    }

    private static void drawSwitch(GraphicsContext g, double size, boolean dimmer, boolean dp) {
        double s = size * 0.4;
        g.fillRoundRect(-s, -s, s * 2, s * 2, 4, 4);
        g.strokeRoundRect(-s, -s, s * 2, s * 2, 4, 4);
        g.strokeLine(-s * 0.5, 0, s * 0.55, -s * 0.45);
        g.fillOval(s * 0.45, -s * 0.55, s * 0.25, s * 0.25);
        if (dimmer) {
            g.strokeOval(-s * 0.35, s * 0.15, s * 0.7, s * 0.55);
        }
        label(g, dp ? "DP" : "SW", 0, s + 10, size);
    }

    private static void drawLight(GraphicsContext g, double size, String key) {
        double r = size * 0.4;
        g.strokeOval(-r, -r, r * 2, r * 2);
        g.strokeLine(-r * 0.65, -r * 0.65, r * 0.65, r * 0.65);
        g.strokeLine(r * 0.65, -r * 0.65, -r * 0.65, r * 0.65);
        if (key.contains("fluorescent") || key.contains("panel")) {
            g.strokeRect(-r * 1.1, -r * 0.35, r * 2.2, r * 0.7);
        }
        if (key.contains("bulkhead")) {
            g.strokeOval(-r * 1.15, -r * 1.15, r * 2.3, r * 2.3);
        }
        label(g, "L", 0, r + 10, size);
    }

    private static void drawBreaker(GraphicsContext g, double size, String tag) {
        double w = size * 0.55;
        double h = size * 0.75;
        g.fillRect(-w / 2, -h / 2, w, h);
        g.strokeRect(-w / 2, -h / 2, w, h);
        g.strokeLine(0, -h / 2, 0, h / 2);
        label(g, tag, 0, h / 2 + 10, size);
    }

    private static void drawDb(GraphicsContext g, double size) {
        double w = size * 0.9;
        double h = size * 0.7;
        g.fillRect(-w / 2, -h / 2, w, h);
        g.strokeRect(-w / 2, -h / 2, w, h);
        for (int i = 0; i < 4; i++) {
            double x = -w / 2 + 4 + i * (w - 8) / 3.5;
            g.strokeLine(x, -h / 2 + 4, x, h / 2 - 4);
        }
        label(g, "DB", 0, h / 2 + 10, size);
    }

    private static void drawCable(GraphicsContext g, double size) {
        double len = size * 0.7;
        g.strokeLine(-len, 0, len, 0);
        g.strokeLine(-len, -3, len, -3);
        g.fillOval(-len - 3, -4, 6, 8);
        g.fillOval(len - 3, -4, 6, 8);
        label(g, "Cu", 0, 12, size);
    }

    private static void drawEarth(GraphicsContext g, double size) {
        double s = size * 0.35;
        g.strokeLine(0, -s, 0, s * 0.2);
        g.strokeLine(-s, s * 0.2, s, s * 0.2);
        g.strokeLine(-s * 0.65, s * 0.45, s * 0.65, s * 0.45);
        g.strokeLine(-s * 0.35, s * 0.7, s * 0.35, s * 0.7);
        label(g, "E", 0, s + 12, size);
    }

    private static void drawConduit(GraphicsContext g, double size) {
        double w = size * 0.85;
        double h = size * 0.35;
        g.strokeRoundRect(-w / 2, -h / 2, w, h, 6, 6);
        label(g, "∅", 0, h / 2 + 10, size);
    }

    private static void drawJunction(GraphicsContext g, double size) {
        double s = size * 0.35;
        g.strokeRect(-s, -s, s * 2, s * 2);
        g.strokeLine(-s, 0, s, 0);
        g.strokeLine(0, -s, 0, s);
        label(g, "JB", 0, s + 10, size);
    }

    private static void drawIsolator(GraphicsContext g, double size, boolean changeover) {
        double s = size * 0.4;
        g.strokeRect(-s, -s, s * 2, s * 2);
        g.strokeLine(-s * 0.5, s * 0.3, s * 0.5, -s * 0.3);
        if (changeover) {
            g.strokeLine(-s * 0.5, -s * 0.3, s * 0.5, s * 0.3);
        }
        label(g, changeover ? "COS" : "ISO", 0, s + 10, size);
    }

    private static void drawMeter(GraphicsContext g, double size) {
        double r = size * 0.4;
        g.strokeOval(-r, -r, r * 2, r * 2);
        g.strokeRect(-r * 0.5, -r * 0.25, r, r * 0.5);
        label(g, "kWh", 0, r + 10, size);
    }

    private static void drawSpd(GraphicsContext g, double size) {
        double s = size * 0.4;
        g.strokePolygon(
                new double[]{-s, 0, s, 0},
                new double[]{0, -s, 0, s},
                4
        );
        label(g, "SPD", 0, s + 10, size);
    }

    private static void drawGeneric(GraphicsContext g, double size, String text) {
        double s = size * 0.4;
        g.fillOval(-s, -s, s * 2, s * 2);
        g.strokeOval(-s, -s, s * 2, s * 2);
        label(g, text, 0, s + 10, size);
    }

    private static void label(GraphicsContext g, String text, double x, double y, double size) {
        g.setFill(Color.web("#9aa6b8"));
        g.setFont(Font.font("Segoe UI", FontWeight.NORMAL, Math.max(9, size * 0.28)));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(text, x, y);
    }

    private static String shortLabel(String key) {
        if (key.length() <= 4) {
            return key.toUpperCase();
        }
        return key.substring(0, 3).toUpperCase();
    }

    /** Suggested hit radius in plan millimetres. */
    public static double hitRadiusMm() {
        return 350;
    }

    /** Suggested on-screen size in pixels (clamped). */
    public static double screenSize(double scale) {
        return Math.clamp(28 + scale * 400, 22, 48);
    }
}
