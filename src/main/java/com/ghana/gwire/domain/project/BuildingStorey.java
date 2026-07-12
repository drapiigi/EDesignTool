package com.ghana.gwire.domain.project;

import com.ghana.gwire.domain.floorplan.FloorPlan;

import java.util.Objects;
import java.util.UUID;

/**
 * One storey / level of a multi-storey building (Phase 9).
 * Level 0 = ground floor; positive = upper floors; negative = basement.
 */
public final class BuildingStorey {

    private final String id;
    private String name;
    private int level;
    private final FloorPlan floorPlan;

    public BuildingStorey(String name, int level) {
        this(UUID.randomUUID().toString(), name, level, new FloorPlan());
    }

    public BuildingStorey(String id, String name, int level, FloorPlan floorPlan) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.isBlank() ? "Floor" : name;
        this.level = level;
        this.floorPlan = floorPlan == null ? new FloorPlan() : floorPlan;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Floor" : name;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public FloorPlan floorPlan() {
        return floorPlan;
    }

    public String displayLabel() {
        if (level == 0) {
            return name + " (G)";
        }
        if (level > 0) {
            return name + " (L" + level + ")";
        }
        return name + " (B" + Math.abs(level) + ")";
    }

    @Override
    public String toString() {
        return displayLabel();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildingStorey that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
