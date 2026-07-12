package com.ghana.gwire.domain.geometry;

/**
 * Line segment utilities for walls and hit-testing.
 */
public final class Segment2 {

    private Segment2() {
    }

    public static double length(Vec2 a, Vec2 b) {
        return a.distanceTo(b);
    }

    /**
     * Shortest distance from point P to segment AB.
     */
    public static double distancePointToSegment(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.subtract(a);
        double abLen2 = ab.dot(ab);
        if (abLen2 < 1e-12) {
            return p.distanceTo(a);
        }
        double t = p.subtract(a).dot(ab) / abLen2;
        t = Math.clamp(t, 0.0, 1.0);
        Vec2 closest = a.lerp(b, t);
        return p.distanceTo(closest);
    }

    /**
     * Parameter t in [0,1] of the closest point on AB to P.
     */
    public static double closestT(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.subtract(a);
        double abLen2 = ab.dot(ab);
        if (abLen2 < 1e-12) {
            return 0;
        }
        double t = p.subtract(a).dot(ab) / abLen2;
        return Math.clamp(t, 0.0, 1.0);
    }

    public static boolean pointInRect(Vec2 p, double x, double y, double w, double h) {
        return p.x() >= x && p.x() <= x + w && p.y() >= y && p.y() <= y + h;
    }
}
