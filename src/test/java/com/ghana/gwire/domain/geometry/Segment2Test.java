package com.ghana.gwire.domain.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Segment2Test {

    @Test
    void distanceToMidpointOfHorizontalSegmentIsZero() {
        Vec2 a = new Vec2(0, 0);
        Vec2 b = new Vec2(1000, 0);
        Vec2 p = new Vec2(500, 0);
        assertEquals(0, Segment2.distancePointToSegment(p, a, b), 1e-9);
    }

    @Test
    void distanceToOffsetPoint() {
        Vec2 a = new Vec2(0, 0);
        Vec2 b = new Vec2(1000, 0);
        Vec2 p = new Vec2(500, 100);
        assertEquals(100, Segment2.distancePointToSegment(p, a, b), 1e-9);
    }

    @Test
    void closestTClampedBeyondEnds() {
        Vec2 a = new Vec2(0, 0);
        Vec2 b = new Vec2(100, 0);
        assertEquals(0, Segment2.closestT(new Vec2(-50, 0), a, b), 1e-9);
        assertEquals(1, Segment2.closestT(new Vec2(200, 0), a, b), 1e-9);
        assertEquals(0.5, Segment2.closestT(new Vec2(50, 10), a, b), 1e-9);
    }

    @Test
    void pointInRect() {
        assertTrue(Segment2.pointInRect(new Vec2(5, 5), 0, 0, 10, 10));
    }
}
