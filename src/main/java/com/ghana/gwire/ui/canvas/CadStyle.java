package com.ghana.gwire.ui.canvas;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * AutoCAD-inspired model-space palette and stroke helpers.
 *
 * <p>Black model space, white geometry, cyan accents, yellow selection —
 * with line weights that stay crisp at architectural plan scales (1:50–1:200).
 */
public final class CadStyle {

    /** Classic AutoCAD model-space black. */
    public static final Color PAPER = Color.web("#000000");
    public static final Color GRID_MINOR = Color.web("#1a1a1a");
    public static final Color GRID_MAJOR = Color.web("#2e2e2e");
    public static final Color GRID_ORIGIN = Color.web("#00aa55", 0.85);

    /** Wall body (solid hatch feel). */
    public static final Color WALL_FILL = Color.web("#d0d0d0");
    public static final Color WALL_OUTLINE = Color.web("#ffffff");
    public static final Color WALL_SEL = Color.web("#ffff00");
    public static final Color WALL_SEL_FILL = Color.web("#ffff00", 0.40);

    public static final Color ROOM_FILL = Color.web("#0c0c0c");
    public static final Color ROOM_FILL_SEL = Color.web("#1a2a22");
    public static final Color ROOM_TEXT = Color.web("#c8c8c8");
    public static final Color ROOM_DIM = Color.web("#808080");

    public static final Color DOOR = Color.web("#ff9966");
    public static final Color DOOR_SWING = Color.web("#ff9966", 0.55);
    public static final Color WINDOW = Color.web("#00d4ff");
    public static final Color WINDOW_GLASS = Color.web("#00aacc", 0.22);

    public static final Color WIRING = Color.web("#66ff66", 0.90);
    public static final Color PREVIEW = Color.web("#00ff88");
    public static final Color HUD_BG = Color.web("#0a0a0a", 0.92);
    public static final Color HUD_BORDER = Color.web("#404040");
    public static final Color HUD_TEXT = Color.web("#b0b0b0");
    public static final Color ACCENT = Color.web("#00d4ff");
    public static final Color DIM = Color.web("#555555");
    public static final Color SCALE_TICK = Color.web("#e0e0e0");

    /** Default Ghana block wall thickness (mm). */
    public static final double DEFAULT_WALL_MM = 150;
    /** Minimum on-screen wall thickness so thin walls stay visible when zoomed out. */
    public static final double MIN_WALL_PX = 2.0;
    /** Skip drawing grid lines denser than this (px). */
    public static final double MIN_GRID_SPACING_PX = 8.0;

    private CadStyle() {
    }

    public static void applyCadStroke(GraphicsContext g, double lineWidthPx) {
        g.setLineWidth(Math.max(0.6, lineWidthPx));
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setLineDashes(null);
        g.setGlobalAlpha(1.0);
    }

    public static void applySharpStroke(GraphicsContext g, double lineWidthPx) {
        g.setLineWidth(Math.max(0.5, lineWidthPx));
        g.setLineCap(StrokeLineCap.BUTT);
        g.setLineJoin(StrokeLineJoin.MITER);
        g.setMiterLimit(2.5);
        g.setLineDashes(null);
        g.setGlobalAlpha(1.0);
    }

    /**
     * AutoCAD-like constant-ish lineweight: slightly thicker when zoomed in,
     * never hair-invisible when zoomed out.
     */
    public static double lineWeight(double nominalPx, double scale) {
        // Mild scale influence so geometry thickens with zoom but stays readable
        double s = Math.clamp(scale / 0.06, 0.65, 1.6);
        return Math.clamp(nominalPx * s, 0.6, 3.5);
    }

    public static Font labelFont(double px) {
        return Font.font("Consolas", FontWeight.NORMAL, Math.max(8, px));
    }

    public static Font smallFont(double px) {
        return Font.font("Consolas", FontWeight.NORMAL, Math.max(7, px));
    }

    public static Font roomTitleFont(double scale) {
        // ~450 mm text height on plan, clamped for screen
        double px = Math.clamp(450 * scale, 9, 16);
        return Font.font("Consolas", FontWeight.BOLD, px);
    }

    public static Font roomMetaFont(double scale) {
        double px = Math.clamp(280 * scale, 7.5, 12);
        return Font.font("Consolas", FontWeight.NORMAL, px);
    }

    /** Half wall thickness in plan mm, boosted if thinner than {@link #MIN_WALL_PX} on screen. */
    public static double wallHalfMm(double thicknessMm, double scale) {
        double t = thicknessMm > 1 ? thicknessMm : DEFAULT_WALL_MM;
        double half = t / 2.0;
        double minHalf = (MIN_WALL_PX / 2.0) / Math.max(scale, 1e-6);
        return Math.max(half, minHalf);
    }

    public static double mmToPx(double mm, double scale, double minPx, double maxPx) {
        return Math.clamp(mm * scale, minPx, maxPx);
    }

    /** Nice round scale-bar length in millimetres for the current zoom. */
    public static double niceScaleBarMm(double scale) {
        double targetPx = 100;
        double rawMm = targetPx / Math.max(scale, 1e-6);
        double[] candidates = {500, 1000, 2000, 2500, 5000, 10000, 20000, 50000};
        double best = candidates[0];
        double bestErr = Double.MAX_VALUE;
        for (double c : candidates) {
            double err = Math.abs(c - rawMm);
            if (err < bestErr) {
                bestErr = err;
                best = c;
            }
        }
        return best;
    }

    /**
     * Approximate architectural print scale (1:N) assuming ~96 CSS px/inch.
     * Useful for HUD; not a paper plot guarantee.
     */
    public static int approxDrawingScale(double scalePxPerMm) {
        // real mm per screen px = 1/scale; physical mm per px ≈ 25.4/96
        double n = 96.0 / (Math.max(scalePxPerMm, 1e-6) * 25.4);
        int[] nice = {20, 25, 50, 75, 100, 125, 150, 200, 250, 500, 1000};
        int best = nice[0];
        double bestErr = Double.MAX_VALUE;
        for (int c : nice) {
            double err = Math.abs(c - n);
            if (err < bestErr) {
                bestErr = err;
                best = c;
            }
        }
        return best;
    }
}
