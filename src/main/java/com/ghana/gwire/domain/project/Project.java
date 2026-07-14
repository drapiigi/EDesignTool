package com.ghana.gwire.domain.project;

import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.electrical.ChecklistReview;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.electrical.ConsumerUnit;
import com.ghana.gwire.domain.floorplan.FloorPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Top-level design project with multi-storey support (Phase 9)
 * and first-class electrical model (Phase 14).
 * {@link #floorPlan()} returns the <em>active</em> storey's plan for backward compatibility.
 */
public final class Project {

    private final String id;
    private String name;
    private final Instant createdAt;
    private Instant modifiedAt;
    private final ProjectSettings settings;
    private final List<BuildingStorey> storeys = new ArrayList<>();
    private int activeStoreyIndex;
    private DesignReport lastReport;
    private final List<Circuit> circuits = new ArrayList<>();
    private ConsumerUnit consumerUnit;
    private final ChecklistReview checklistReview = new ChecklistReview();

    public Project(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name == null || name.isBlank() ? "Untitled project" : name;
        this.createdAt = Instant.now();
        this.modifiedAt = createdAt;
        this.settings = new ProjectSettings();
        this.storeys.add(new BuildingStorey("Ground floor", 0));
        this.activeStoreyIndex = 0;
    }

    /**
     * Reconstruct a project from persistence (preserves id and timestamps).
     * Starts with an empty ground floor; loaders replace storeys as needed.
     */
    public Project(String id, String name, Instant createdAt, Instant modifiedAt) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.isBlank() ? "Untitled project" : name;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.modifiedAt = modifiedAt == null ? this.createdAt : modifiedAt;
        this.settings = new ProjectSettings();
        this.storeys.add(new BuildingStorey("Ground floor", 0));
        this.activeStoreyIndex = 0;
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

    /**
     * Active storey's floor plan (used by existing UI/calc/export code).
     */
    public FloorPlan floorPlan() {
        return activeStorey().floorPlan();
    }

    public List<BuildingStorey> storeys() {
        return Collections.unmodifiableList(storeys);
    }

    public int activeStoreyIndex() {
        return activeStoreyIndex;
    }

    public BuildingStorey activeStorey() {
        if (storeys.isEmpty()) {
            storeys.add(new BuildingStorey("Ground floor", 0));
            activeStoreyIndex = 0;
        }
        return storeys.get(Math.clamp(activeStoreyIndex, 0, storeys.size() - 1));
    }

    public void setActiveStoreyIndex(int index) {
        if (storeys.isEmpty()) {
            return;
        }
        int next = Math.clamp(index, 0, storeys.size() - 1);
        if (next == activeStoreyIndex) {
            return;
        }
        activeStoreyIndex = next;
        touch();
    }

    public BuildingStorey addStorey(String name, int level) {
        BuildingStorey s = new BuildingStorey(name, level);
        storeys.add(s);
        touch();
        return s;
    }

    public boolean removeStoreyAt(int index) {
        if (storeys.size() <= 1 || index < 0 || index >= storeys.size()) {
            return false;
        }
        storeys.remove(index);
        if (activeStoreyIndex >= storeys.size()) {
            activeStoreyIndex = storeys.size() - 1;
        }
        touch();
        return true;
    }

    /** Replace storey list (used by persistence loader). */
    public void replaceStoreys(List<BuildingStorey> next, int activeIndex) {
        storeys.clear();
        if (next == null || next.isEmpty()) {
            storeys.add(new BuildingStorey("Ground floor", 0));
            activeStoreyIndex = 0;
        } else {
            storeys.addAll(next);
            activeStoreyIndex = Math.clamp(activeIndex, 0, storeys.size() - 1);
        }
        touch();
    }

    /** Total devices across all storeys. */
    public int totalDeviceCount() {
        int n = 0;
        for (BuildingStorey s : storeys) {
            n += s.floorPlan().devices().size();
        }
        return n;
    }

    public int totalRoomCount() {
        int n = 0;
        for (BuildingStorey s : storeys) {
            n += s.floorPlan().rooms().size();
        }
        return n;
    }

    /** Most recent calculation report, if any (Phase 4). */
    public DesignReport lastReport() {
        return lastReport;
    }

    public void setLastReport(DesignReport lastReport) {
        if (this.lastReport == lastReport) {
            return;
        }
        this.lastReport = lastReport;
        touch();
    }

    public List<Circuit> circuits() {
        return Collections.unmodifiableList(circuits);
    }

    public void setCircuits(List<Circuit> next) {
        circuits.clear();
        if (next != null) {
            for (Circuit c : next) {
                if (c != null) {
                    circuits.add(c);
                }
            }
        }
        touch();
    }

    public void addCircuit(Circuit circuit) {
        if (circuit != null) {
            circuits.add(circuit);
            touch();
        }
    }

    public Optional<Circuit> findCircuit(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return circuits.stream().filter(c -> id.equals(c.id())).findFirst();
    }

    public ConsumerUnit consumerUnit() {
        return consumerUnit;
    }

    public void setConsumerUnit(ConsumerUnit consumerUnit) {
        this.consumerUnit = consumerUnit;
        touch();
    }

    public ConsumerUnit ensureConsumerUnit() {
        if (consumerUnit == null) {
            consumerUnit = new ConsumerUnit("Main consumer unit", 12);
            touch();
        }
        return consumerUnit;
    }

    public ChecklistReview checklistReview() {
        return checklistReview;
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
