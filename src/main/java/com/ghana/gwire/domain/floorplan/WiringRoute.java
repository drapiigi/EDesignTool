package com.ghana.gwire.domain.floorplan;

import com.ghana.gwire.domain.geometry.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Polyline cable/wiring route on a floor plan (plan mm).
 */
public final class WiringRoute {

    private final String id;
    private String circuitId;
    private String cableComponentId;
    private String label;
    private final List<Vec2> points = new ArrayList<>();

    public WiringRoute(String circuitId, String label) {
        this(UUID.randomUUID().toString(), circuitId, null, label, List.of());
    }

    public WiringRoute(String id, String circuitId, String cableComponentId, String label, List<Vec2> points) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.circuitId = circuitId;
        this.cableComponentId = cableComponentId;
        this.label = label == null ? "" : label;
        if (points != null) {
            this.points.addAll(points);
        }
    }

    public String id() {
        return id;
    }

    public String circuitId() {
        return circuitId;
    }

    public void setCircuitId(String circuitId) {
        this.circuitId = circuitId;
    }

    public String cableComponentId() {
        return cableComponentId;
    }

    public void setCableComponentId(String cableComponentId) {
        this.cableComponentId = cableComponentId;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public List<Vec2> points() {
        return Collections.unmodifiableList(points);
    }

    public void setPoints(List<Vec2> pts) {
        points.clear();
        if (pts != null) {
            points.addAll(pts);
        }
    }

    public void addPoint(Vec2 p) {
        if (p != null) {
            points.add(p);
        }
    }

    /** Approximate route length in metres. */
    public double lengthM() {
        double mm = 0;
        for (int i = 1; i < points.size(); i++) {
            mm += points.get(i - 1).distanceTo(points.get(i));
        }
        return mm / 1000.0;
    }

    public WiringRoute copy() {
        List<Vec2> pts = new ArrayList<>(points);
        return new WiringRoute(id, circuitId, cableComponentId, label, pts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WiringRoute that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
