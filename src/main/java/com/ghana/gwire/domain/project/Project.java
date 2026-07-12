package com.ghana.gwire.domain.project;

import com.ghana.gwire.domain.floorplan.FloorPlan;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Top-level design project. Phase 2 owns floor plan geometry; later phases
 * attach circuits, BOQ, and AI artefacts.
 */
public final class Project {

    private final String id;
    private String name;
    private final Instant createdAt;
    private Instant modifiedAt;
    private final ProjectSettings settings;
    private final FloorPlan floorPlan;

    public Project(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name == null || name.isBlank() ? "Untitled project" : name;
        this.createdAt = Instant.now();
        this.modifiedAt = createdAt;
        this.settings = new ProjectSettings();
        this.floorPlan = new FloorPlan();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Untitled project" : name;
        touch();
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant modifiedAt() {
        return modifiedAt;
    }

    public void touch() {
        this.modifiedAt = Instant.now();
    }

    public ProjectSettings settings() {
        return settings;
    }

    public FloorPlan floorPlan() {
        return floorPlan;
    }

    public String supplySummary() {
        return "%.0f V / %.0f Hz · %s".formatted(
                settings.nominalVoltageV(),
                settings.frequencyHz(),
                settings.houseType()
        );
    }

    @Override
    public String toString() {
        return Objects.toString(name);
    }
}
