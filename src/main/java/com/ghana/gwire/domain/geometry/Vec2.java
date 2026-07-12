package com.ghana.gwire.domain.geometry;

/**
 * Immutable 2D vector in plan millimetres (world space).
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 subtract(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 scale(double s) {
        return new Vec2(x * s, y * s);
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public double distanceTo(Vec2 o) {
        return subtract(o).length();
    }

    public Vec2 normalize() {
        double len = length();
        if (len < 1e-9) {
            return ZERO;
        }
        return scale(1.0 / len);
    }

    public double dot(Vec2 o) {
        return x * o.x + y * o.y;
    }

    /** Orthogonal unit vector (rotated 90° CCW). */
    public Vec2 perpendicular() {
        return new Vec2(-y, x).normalize();
    }

    public Vec2 lerp(Vec2 o, double t) {
        return new Vec2(x + (o.x - x) * t, y + (o.y - y) * t);
    }

    public Vec2 snap(double gridMm) {
        if (gridMm <= 0) {
            return this;
        }
        return new Vec2(
                Math.round(x / gridMm) * gridMm,
                Math.round(y / gridMm) * gridMm
        );
    }
}
