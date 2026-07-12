package com.ghana.gwire.ui.canvas;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Shared CAD-style palette and stroke helpers for the floor-plan canvas.
 * Nord-inspired dark paper for high-contrast electrical plan symbols.
 */
public final class CadStyle {

    public static final Color PAPER = Color.web("#0b0f14");
    public static final Color GRID_MINOR = Color.web("#151c28");
    public static final Color GRID_MAJOR = Color.web("#243044");
    public static final Color GRID_ORIGIN = Color.web("#3d9e6a", 0.70);
    public static final Color WALL = Color.web("#e5e9f0");
    public static final Color WALL_FILL = Color.web("#4c566a");
    public static final Color WALL_OUTLINE = Color.web("#eceff4");
    public static final Color WALL_SEL = Color.web("#ebcb8b");
    public static final Color WALL_SEL_FILL = Color.web("#ebcb8b", 0.35);
    public static final Color ROOM_FILL = Color.web("#1a2636", 0.62);
    public static final Color ROOM_FILL_SEL = Color.web("#2e4a5e", 0.72);
    public static final Color ROOM_EDGE = Color.web("#5e81ac");
    public static final Color ROOM_SEL = Color.web("#88c0d0");
    public static final Color ROOM_TEXT = Color.web("#e5e9f0");
    public static final Color ROOM_DIM = Color.web("#8f9bb0");
    public static final Color DOOR = Color.web("#d08770");
    public static final Color DOOR_SWING = Color.web("#d08770", 0.55);
    public static final Color WINDOW = Color.web("#81a1c1");
    public static final Color WINDOW_GLASS = Color.web("#88c0d0", 0.35);
    public static final Color WIRING = Color.web("#a3be8c", 0.90);
    public static final Color PREVIEW = Color.web("#a3be8c");
    public static final Color HUD_BG = Color.web("#0b0f14", 0.90);
    public static final Color HUD_BORDER = Color.web("#3b4252");
    public static final Color HUD_TEXT = Color.web("#aeb7c6");
    public static final Color ACCENT = Color.web("#88c0d0");
    public static final Color DIM = Color.web("#6b7280");
    public static final Color SCALE_TICK = Color.web("#d8dee9");

    private CadStyle() {
    }

    public static void applyCadStroke(GraphicsContext g, double lineWidthPx) {
        g.setLineWidth(Math.max(0.75, lineWidthPx));
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setLineDashes(null);
        g.setGlobalAlpha(1.0);
    }

    public static void applySharpStroke(GraphicsContext g, double lineWidthPx) {
        g.setLineWidth(Math.max(0.6, lineWidthPx));
        g.setLineCap(StrokeLineCap.BUTT);
        g.setLineJoin(StrokeLineJoin.MITER);
        g.setLineDashes(null);
        g.setGlobalAlpha(1.0);
    }

    public static Font labelFont(double px) {
        return Font.font("Segoe UI", FontWeight.SEMI_BOLD, Math.max(9, px));
    }

    public static Font smallFont(double px) {
        return Font.font("Segoe UI", FontWeight.NORMAL, Math.max(8, px));
    }

    /** Screen pixels for a plan millimetre thickness, clamped for readability. */
    public static double mmToPx(double mm, double scale, double minPx, double maxPx) {
        return Math.clamp(mm * scale, minPx, maxPx);
    }

    /** Nice round scale-bar length in millimetres for the current zoom. */
    public static double niceScaleBarMm(double scale) {
        // target ~100–140 px
        double targetPx = 120;
        double rawMm = targetPx / Math.max(scale, 1e-6);
        double[] candidates = {500, 1000, 2000, 5000, 10000, 20000, 50000};
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
}
