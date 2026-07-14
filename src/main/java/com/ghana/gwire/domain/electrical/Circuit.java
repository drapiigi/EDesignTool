package com.ghana.gwire.domain.electrical;

import com.ghana.gwire.domain.calc.CircuitKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent final circuit (Phase 14) — stable id across save/load.
 */
public final class Circuit {

    private final String id;
    private String name;
    private CircuitKind kind;
    private String roomId;
    private final List<String> deviceIds = new ArrayList<>();
    /** Consumer-unit way number (1-based); 0 = unassigned. */
    private int wayNumber;
    /** RCD group label, e.g. "RCD-A". */
    private String rcdGroup = "";
    private double breakerA;
    private String cableComponentId = "";
    private String cableSize = "";
    private double estimatedLengthM;
    private String notes = "";

    public Circuit(String name, CircuitKind kind) {
        this(UUID.randomUUID().toString(), name, kind, null);
    }

    public Circuit(String id, String name, CircuitKind kind, String roomId) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.isBlank() ? "Circuit" : name;
        this.kind = kind == null ? CircuitKind.OTHER : kind;
        this.roomId = roomId;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Circuit" : name;
    }

    public CircuitKind kind() {
        return kind;
    }

    public void setKind(CircuitKind kind) {
        this.kind = kind == null ? CircuitKind.OTHER : kind;
    }

    public String roomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public List<String> deviceIds() {
        return Collections.unmodifiableList(deviceIds);
    }

    public void setDeviceIds(List<String> ids) {
        deviceIds.clear();
        if (ids != null) {
            for (String id : ids) {
                addDeviceId(id);
            }
        }
    }

    public void addDeviceId(String deviceId) {
        if (deviceId != null && !deviceId.isBlank() && !deviceIds.contains(deviceId)) {
            deviceIds.add(deviceId);
        }
    }

    public void removeDeviceId(String deviceId) {
        deviceIds.remove(deviceId);
    }

    public int wayNumber() {
        return wayNumber;
    }

    public void setWayNumber(int wayNumber) {
        this.wayNumber = Math.max(0, wayNumber);
    }

    public String rcdGroup() {
        return rcdGroup;
    }

    public void setRcdGroup(String rcdGroup) {
        this.rcdGroup = rcdGroup == null ? "" : rcdGroup.trim();
    }

    public double breakerA() {
        return breakerA;
    }

    public void setBreakerA(double breakerA) {
        this.breakerA = Math.max(0, breakerA);
    }

    public String cableComponentId() {
        return cableComponentId;
    }

    public void setCableComponentId(String cableComponentId) {
        this.cableComponentId = cableComponentId == null ? "" : cableComponentId;
    }

    public String cableSize() {
        return cableSize;
    }

    public void setCableSize(String cableSize) {
        this.cableSize = cableSize == null ? "" : cableSize;
    }

    public double estimatedLengthM() {
        return estimatedLengthM;
    }

    public void setEstimatedLengthM(double estimatedLengthM) {
        this.estimatedLengthM = Math.max(0, estimatedLengthM);
    }

    public String notes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public Circuit copy() {
        Circuit c = new Circuit(id, name, kind, roomId);
        c.setDeviceIds(deviceIds);
        c.setWayNumber(wayNumber);
        c.setRcdGroup(rcdGroup);
        c.setBreakerA(breakerA);
        c.setCableComponentId(cableComponentId);
        c.setCableSize(cableSize);
        c.setEstimatedLengthM(estimatedLengthM);
        c.setNotes(notes);
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Circuit that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + kind + ")";
    }
}
