package com.ghana.gwire.domain.floorplan;

import java.util.Objects;
import java.util.UUID;

/**
 * Door or window placed along a wall ({@code t} is 0..1 along wall start→end).
 */
public final class Opening {

    private final String id;
    private final String wallId;
    private OpeningType type;
    private double t;
    private double widthMm;

    public Opening(String wallId, OpeningType type, double t, double widthMm) {
        this(UUID.randomUUID().toString(), wallId, type, t, widthMm);
    }

    public Opening(String id, String wallId, OpeningType type, double t, double widthMm) {
        this.id = Objects.requireNonNull(id);
        this.wallId = Objects.requireNonNull(wallId);
        this.type = Objects.requireNonNull(type);
        this.t = Math.clamp(t, 0.0, 1.0);
        this.widthMm = Math.max(100, widthMm);
    }

    public String id() {
        return id;
    }

    public String wallId() {
        return wallId;
    }

    public OpeningType type() {
        return type;
    }

    public void setType(OpeningType type) {
        this.type = Objects.requireNonNull(type);
    }

    public double t() {
        return t;
    }

    public void setT(double t) {
        this.t = Math.clamp(t, 0.0, 1.0);
    }

    public double widthMm() {
        return widthMm;
    }

    public void setWidthMm(double widthMm) {
        this.widthMm = Math.max(100, widthMm);
    }

    public Opening copy() {
        return new Opening(id, wallId, type, t, widthMm);
    }
}
