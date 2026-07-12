package com.ghana.gwire.domain.floorplan;

import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;

import java.util.Objects;
import java.util.UUID;

/**
 * Structural wall segment in plan millimetres.
 */
public final class Wall {

    private final String id;
    private Vec2 start;
    private Vec2 end;
    /** Nominal thickness in mm (for future 3D/section views). */
    private double thicknessMm;

    public Wall(Vec2 start, Vec2 end) {
        this(UUID.randomUUID().toString(), start, end, 150);
    }

    public Wall(String id, Vec2 start, Vec2 end, double thicknessMm) {
        this.id = Objects.requireNonNull(id);
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.thicknessMm = thicknessMm;
    }

    public String id() {
        return id;
    }

    public Vec2 start() {
        return start;
    }

    public Vec2 end() {
        return end;
    }

    public void setStart(Vec2 start) {
        this.start = Objects.requireNonNull(start);
    }

    public void setEnd(Vec2 end) {
        this.end = Objects.requireNonNull(end);
    }

    public double thicknessMm() {
        return thicknessMm;
    }

    public void setThicknessMm(double thicknessMm) {
        this.thicknessMm = thicknessMm;
    }

    public double lengthMm() {
        return Segment2.length(start, end);
    }

    public Wall copy() {
        return new Wall(id, start, end, thicknessMm);
    }
}
