package com.ghana.gwire.domain.floorplan;

import com.ghana.gwire.domain.geometry.Segment2;
import com.ghana.gwire.domain.geometry.Vec2;

import java.util.Objects;
import java.util.UUID;

/**
 * Axis-aligned room rectangle in plan millimetres (Phase 2).
 * Named rooms feed later electrical zoning and diversity (L.I. 2008 practice).
 */
public final class Room {

    private final String id;
    private String name;
    private double x;
    private double y;
    private double widthMm;
    private double heightMm;

    public Room(String name, double x, double y, double widthMm, double heightMm) {
        this(UUID.randomUUID().toString(), name, x, y, widthMm, heightMm);
    }

    public Room(String id, String name, double x, double y, double widthMm, double heightMm) {
        this.id = Objects.requireNonNull(id);
        this.name = name == null || name.isBlank() ? "Room" : name;
        this.x = x;
        this.y = y;
        this.widthMm = Math.max(1, widthMm);
        this.heightMm = Math.max(1, heightMm);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Room" : name;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double widthMm() {
        return widthMm;
    }

    public double heightMm() {
        return heightMm;
    }

    public void setBounds(double x, double y, double widthMm, double heightMm) {
        this.x = x;
        this.y = y;
        this.widthMm = Math.max(1, widthMm);
        this.heightMm = Math.max(1, heightMm);
    }

    /** Area in square metres. */
    public double areaM2() {
        return (widthMm * heightMm) / 1_000_000.0;
    }

    public boolean contains(Vec2 p) {
        return Segment2.pointInRect(p, x, y, widthMm, heightMm);
    }

    public Room copy() {
        return new Room(id, name, x, y, widthMm, heightMm);
    }
}
