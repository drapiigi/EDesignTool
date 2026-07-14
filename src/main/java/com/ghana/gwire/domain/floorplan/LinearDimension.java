package com.ghana.gwire.domain.floorplan;

import com.ghana.gwire.domain.geometry.Vec2;

import java.util.Objects;
import java.util.UUID;

/**
 * Linear dimension annotation between two plan points (Phase 13b).
 * Display length is always derived from endpoints (mm).
 */
public final class LinearDimension {

    private final String id;
    private Vec2 p1;
    private Vec2 p2;
    /** Offset of the dimension line from the measured segment (mm, signed). */
    private double offsetMm;
    private String labelOverride;

    public LinearDimension(Vec2 p1, Vec2 p2) {
        this(UUID.randomUUID().toString(), p1, p2, 400, null);
    }

    public LinearDimension(String id, Vec2 p1, Vec2 p2, double offsetMm, String labelOverride) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.p1 = Objects.requireNonNull(p1, "p1");
        this.p2 = Objects.requireNonNull(p2, "p2");
        this.offsetMm = offsetMm;
        this.labelOverride = labelOverride;
    }

    public String id() {
        return id;
    }

    public Vec2 p1() {
        return p1;
    }

    public Vec2 p2() {
        return p2;
    }

    public void setP1(Vec2 p1) {
        this.p1 = Objects.requireNonNull(p1);
    }

    public void setP2(Vec2 p2) {
        this.p2 = Objects.requireNonNull(p2);
    }

    public double offsetMm() {
        return offsetMm;
    }

    public void setOffsetMm(double offsetMm) {
        this.offsetMm = offsetMm;
    }

    public String labelOverride() {
        return labelOverride;
    }

    public void setLabelOverride(String labelOverride) {
        this.labelOverride = labelOverride;
    }

    public double lengthMm() {
        return p1.distanceTo(p2);
    }

    /** Text shown on plan (override or auto mm/m). */
    public String displayLabel() {
        if (labelOverride != null && !labelOverride.isBlank()) {
            return labelOverride;
        }
        double len = lengthMm();
        if (len >= 1000) {
            return String.format("%.2f m", len / 1000.0);
        }
        return String.format("%.0f mm", len);
    }

    public LinearDimension copy() {
        return new LinearDimension(id, p1, p2, offsetMm, labelOverride);
    }
}
