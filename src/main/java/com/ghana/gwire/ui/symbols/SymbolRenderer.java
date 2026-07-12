package com.ghana.gwire.ui.symbols;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * CAD-quality electrical symbols inspired by IEC 60617 / BS 3939 conventions
 * used on architectural lighting & power drawings.
 *
 * <p>Drawn in local coordinates centred at (0,0); caller applies translate/rotate.
 * White/cyan strokes on dark plans for high contrast; selected state uses amber.
 */
public final class SymbolRenderer {

    private static final Color STROKE = Color.web("#eceff4");
    private static final Color FILL = Color.web("#0b0f14", 0.92);
    private static final Color ACCENT = Color.web("#88c0d0");
    private static final Color SELECT = Color.web("#ebcb8b");
    private static final Color SELECT_FILL = Color.web("#ebcb8b", 0.18);
    private static final Color LABEL = Color.web("#c8d0dc");

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
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setImageSmoothing(true);

        Color stroke = selected ? SELECT : STROKE;
        Color fill = selected ? SELECT_FILL : FILL;
        double lw = selected ? Math.max(1.8, size * 0.07) : Math.max(1.35, size * 0.055);
        g.setStroke(stroke);
        g.setFill(fill);
        g.setLineWidth(lw);

        // Selection halo
        if (selected) {
            g.setStroke(SELECT);
            g.setLineWidth(lw + 2.5);
            g.setGlobalAlpha(0.35);
            g.strokeOval(-size * 0.55, -size * 0.55, size * 1.1, size * 1.1);
            g.setGlobalAlpha(1.0);
            g.setStroke(stroke);
            g.setLineWidth(lw);
        }

        String key = symbolKey.toLowerCase();
        if (key.startsWith("socket")) {
            drawSocket(g, size, key.contains("2g"), key.contains("industrial") || key.contains("15a"));
        } else if (key.startsWith("switch")) {
            drawSwitch(g, size, key.contains("dimmer"),
                    key.contains("dp") || key.contains("20a") || key.contains("45a"),
                    key.contains("intermediate") || key.contains("int"));
        } else if (key.startsWith("light")) {
            drawLight(g, size, key);
        } else if (key.startsWith("mcb") || key.startsWith("rcbo")) {
            drawBreaker(g, size, key.contains("3p") ? "3P" : "1P", key.startsWith("rcbo"));
        } else if (key.startsWith("rccb")) {
            drawRcd(g, size, key.contains("100") ? "100mA" : "30mA");
        } else if (key.startsWith("db")) {
            drawDb(g, size);
        } else if (key.startsWith("cable")) {
            drawCable(g, size);
        } else if (key.startsWith("earth")) {
            drawEarth(g, size, key.contains("rod"), key.contains("bond") || key.contains("bar"));
        } else if (key.startsWith("conduit") || key.startsWith("trunking")) {
            drawConduit(g, size, key.contains("gi") || key.contains("steel"));
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

    // --- IEC-style symbols ---

    /** BS 1363 socket outlet: circle with three-pin mark (architectural plan symbol). */
    private static void drawSocket(GraphicsContext g, double size, boolean twin, boolean industrial) {
        double r = size * 0.42;
        g.fillOval(-r, -r, r * 2, r * 2);
        g.strokeOval(-r, -r, r * 2, r * 2);
        // earth pin (top) + live/neutral
        double pinR = r * 0.11;
        g.setFill(g.getStroke());
        g.fillOval(-pinR, -r * 0.55, pinR * 2, pinR * 2); // earth
        g.fillOval(-r * 0.38 - pinR, r * 0.22, pinR * 2, pinR * 2);
        g.fillOval(r * 0.38 - pinR, r * 0.22, pinR * 2, pinR * 2);
        if (industrial) {
            g.strokeOval(-r * 0.72, -r * 0.72, r * 1.44, r * 1.44);
        }
        if (twin) {
            // second outlet offset (twin gang on plan)
            g.setFill(FILL);
            g.fillOval(r * 0.55, -r * 0.35, r * 0.85, r * 0.85);
            g.strokeOval(r * 0.55, -r * 0.35, r * 0.85, r * 0.85);
            double r2 = r * 0.42;
            double cx = r * 0.55 + r * 0.425;
            double cy = -r * 0.35 + r * 0.425;
            g.setFill(g.getStroke());
            g.fillOval(cx - pinR * 0.8, cy - r2 * 0.4, pinR * 1.6, pinR * 1.6);
        }
        label(g, twin ? "2G" : (industrial ? "IND" : "13A"), 0, r + size * 0.28, size);
    }

    /** Single-pole switch: square plate + angled lever (plan symbol). */
    private static void drawSwitch(GraphicsContext g, double size, boolean dimmer, boolean dp, boolean intermediate) {
        double s = size * 0.38;
        g.fillRect(-s, -s, s * 2, s * 2);
        g.strokeRect(-s, -s, s * 2, s * 2);
        // switch lever
        g.strokeLine(-s * 0.55, s * 0.15, s * 0.45, -s * 0.55);
        g.fillOval(s * 0.38, -s * 0.62, s * 0.22, s * 0.22);
        if (dp) {
            g.strokeLine(-s * 0.55, s * 0.45, s * 0.45, -s * 0.25);
        }
        if (dimmer) {
            g.strokeArc(-s * 0.45, s * 0.05, s * 0.9, s * 0.7, 200, 140, javafx.scene.shape.ArcType.OPEN);
        }
        if (intermediate) {
            g.strokeLine(-s * 0.3, -s * 0.15, s * 0.3, s * 0.35);
        }
        label(g, dp ? "DP" : (dimmer ? "DIM" : "S"), 0, s + size * 0.28, size);
    }

    /** Luminaire: circle with cross (IEC) + variants. */
    private static void drawLight(GraphicsContext g, double size, String key) {
        double r = size * 0.40;
        g.fillOval(-r, -r, r * 2, r * 2);
        g.strokeOval(-r, -r, r * 2, r * 2);
        double c = r * 0.62;
        g.strokeLine(-c, -c, c, c);
        g.strokeLine(c, -c, -c, c);
        if (key.contains("fluorescent") || key.contains("panel")) {
            g.strokeRect(-r * 1.25, -r * 0.38, r * 2.5, r * 0.76);
        }
        if (key.contains("bulkhead")) {
            g.strokeOval(-r * 1.2, -r * 1.2, r * 2.4, r * 2.4);
            // mounting lugs
            g.strokeLine(-r * 1.2, 0, -r * 1.45, 0);
            g.strokeLine(r * 1.2, 0, r * 1.45, 0);
        }
        if (key.contains("led") || key.contains("bulb")) {
            // small rays
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90 + 45);
                g.strokeLine(Math.cos(a) * r * 1.05, Math.sin(a) * r * 1.05,
                        Math.cos(a) * r * 1.28, Math.sin(a) * r * 1.28);
            }
        }
        label(g, key.contains("bulk") ? "BH" : "L", 0, r + size * 0.28, size);
    }

    private static void drawBreaker(GraphicsContext g, double size, String poles, boolean rcbo) {
        double w = size * 0.50;
        double h = size * 0.72;
        g.fillRect(-w / 2, -h / 2, w, h);
        g.strokeRect(-w / 2, -h / 2, w, h);
        // DIN-style toggle
        g.strokeLine(0, -h * 0.35, 0, h * 0.15);
        g.fillRect(-w * 0.18, -h * 0.42, w * 0.36, h * 0.14);
        if (rcbo) {
            g.strokeOval(-w * 0.28, h * 0.18, w * 0.56, h * 0.22);
        }
        label(g, poles, 0, h / 2 + size * 0.26, size);
    }

    private static void drawRcd(GraphicsContext g, double size, String rating) {
        double w = size * 0.62;
        double h = size * 0.55;
        g.fillRoundRect(-w / 2, -h / 2, w, h, 3, 3);
        g.strokeRoundRect(-w / 2, -h / 2, w, h, 3, 3);
        g.strokeOval(-w * 0.22, -h * 0.22, w * 0.44, h * 0.44);
        g.strokeLine(-w * 0.35, 0, w * 0.35, 0);
        label(g, "RCD", 0, h / 2 + size * 0.22, size);
        label(g, rating, 0, h / 2 + size * 0.42, size * 0.85);
    }

    private static void drawDb(GraphicsContext g, double size) {
        double w = size * 0.95;
        double h = size * 0.72;
        g.fillRect(-w / 2, -h / 2, w, h);
        g.strokeRect(-w / 2, -h / 2, w, h);
        // ways
        int ways = 6;
        double inset = w * 0.08;
        double usable = w - 2 * inset;
        for (int i = 0; i < ways; i++) {
            double x = -w / 2 + inset + (i + 0.5) * usable / ways;
            g.strokeLine(x, -h / 2 + h * 0.18, x, h / 2 - h * 0.18);
            g.strokeRect(x - usable / ways * 0.28, -h * 0.12, usable / ways * 0.56, h * 0.22);
        }
        // door hinge mark
        g.strokeLine(-w / 2, -h / 2, -w / 2 - size * 0.08, 0);
        g.strokeLine(-w / 2, h / 2, -w / 2 - size * 0.08, 0);
        label(g, "DB", 0, h / 2 + size * 0.28, size);
    }

    private static void drawCable(GraphicsContext g, double size) {
        double len = size * 0.72;
        g.setLineWidth(Math.max(1.5, size * 0.08));
        g.strokeLine(-len, -size * 0.06, len, -size * 0.06);
        g.strokeLine(-len, size * 0.06, len, size * 0.06);
        // ends
        g.strokeOval(-len - size * 0.08, -size * 0.12, size * 0.16, size * 0.24);
        g.strokeOval(len - size * 0.08, -size * 0.12, size * 0.16, size * 0.24);
        label(g, "Cu", 0, size * 0.38, size);
    }

    private static void drawEarth(GraphicsContext g, double size, boolean rod, boolean bar) {
        double s = size * 0.42;
        g.setLineWidth(Math.max(1.6, size * 0.07));
        // IEC earth symbol: vertical + three decreasing horizontals
        g.strokeLine(0, -s, 0, s * 0.15);
        g.strokeLine(-s, s * 0.15, s, s * 0.15);
        g.strokeLine(-s * 0.68, s * 0.42, s * 0.68, s * 0.42);
        g.strokeLine(-s * 0.36, s * 0.68, s * 0.36, s * 0.68);
        if (rod) {
            g.strokeLine(0, s * 0.15, 0, s * 1.05);
        }
        if (bar) {
            g.strokeRect(-s * 0.9, -s * 0.35, s * 1.8, s * 0.35);
        }
        label(g, rod ? "ROD" : (bar ? "BAR" : "E"), 0, s + size * 0.32, size);
    }

    private static void drawConduit(GraphicsContext g, double size, boolean metal) {
        double w = size * 0.9;
        double h = size * 0.32;
        g.strokeRoundRect(-w / 2, -h / 2, w, h, h, h);
        if (metal) {
            // hatch
            for (double x = -w / 2 + 4; x < w / 2; x += 6) {
                g.strokeLine(x, -h / 2 + 2, x + 4, h / 2 - 2);
            }
        }
        label(g, metal ? "GI" : "PVC", 0, h / 2 + size * 0.28, size);
    }

    private static void drawJunction(GraphicsContext g, double size) {
        double s = size * 0.36;
        g.fillRect(-s, -s, s * 2, s * 2);
        g.strokeRect(-s, -s, s * 2, s * 2);
        g.strokeLine(-s, 0, s, 0);
        g.strokeLine(0, -s, 0, s);
        // terminal dots
        double d = s * 0.18;
        g.setFill(g.getStroke());
        g.fillOval(-d, -d, d * 2, d * 2);
        label(g, "JB", 0, s + size * 0.28, size);
    }

    private static void drawIsolator(GraphicsContext g, double size, boolean changeover) {
        double s = size * 0.40;
        g.fillRect(-s, -s, s * 2, s * 2);
        g.strokeRect(-s, -s, s * 2, s * 2);
        // disconnector symbol
        g.strokeLine(-s * 0.55, s * 0.4, s * 0.15, -s * 0.45);
        g.fillOval(s * 0.1, -s * 0.55, s * 0.2, s * 0.2);
        g.strokeLine(-s * 0.55, s * 0.4, -s * 0.55, s * 0.55);
        if (changeover) {
            g.strokeLine(-s * 0.55, -s * 0.4, s * 0.15, s * 0.45);
        }
        label(g, changeover ? "COS" : "ISO", 0, s + size * 0.28, size);
    }

    private static void drawMeter(GraphicsContext g, double size) {
        double r = size * 0.42;
        g.fillOval(-r, -r, r * 2, r * 2);
        g.strokeOval(-r, -r, r * 2, r * 2);
        g.strokeRect(-r * 0.55, -r * 0.28, r * 1.1, r * 0.56);
        // Wh mark
        g.setLineWidth(Math.max(1.0, size * 0.04));
        g.strokeLine(-r * 0.25, 0, r * 0.25, 0);
        label(g, "kWh", 0, r + size * 0.28, size);
    }

    private static void drawSpd(GraphicsContext g, double size) {
        double s = size * 0.42;
        // diamond / arrow surge symbol
        g.strokePolygon(
                new double[]{0, s, 0, -s},
                new double[]{-s, 0, s, 0},
                4
        );
        g.strokeLine(0, -s * 0.45, 0, s * 0.45);
        label(g, "SPD", 0, s + size * 0.28, size);
    }

    private static void drawGeneric(GraphicsContext g, double size, String text) {
        double s = size * 0.40;
        g.fillOval(-s, -s, s * 2, s * 2);
        g.strokeOval(-s, -s, s * 2, s * 2);
        label(g, text, 0, s + size * 0.28, size);
    }

    private static void label(GraphicsContext g, String text, double x, double y, double size) {
        // Skip annotation text on compact library / thumbnail icons
        if (size < 30) {
            return;
        }
        g.setFill(LABEL);
        g.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, Math.max(8.5, size * 0.26)));
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
        return 400;
    }

    /**
     * On-screen symbol size: scales mildly with zoom so icons stay CAD-readable
     * when zoomed out, without becoming huge when zoomed in.
     */
    public static double screenSize(double scale) {
        // scale is px/mm; at 0.04 ≈ default, want ~34px
        double px = 22 + Math.log10(Math.max(scale, 0.008) * 1000) * 18;
        return Math.clamp(px, 20, 56);
    }
}
