package com.ghana.gwire.domain.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Calculated load and sizing result for one final circuit.
 */
public final class CircuitLoad {

    private final String id;
    private String name;
    private CircuitKind kind;
    private String roomId;
    private final List<String> deviceIds = new ArrayList<>();
    private double connectedLoadW;
    private double designCurrentA;
    private double diversityFactor = 1.0;
    private double afterDiversityLoadW;
    private double afterDiversityCurrentA;
    private double estimatedLengthM;
    private String recommendedCableId;
    private String recommendedCableSize;
    private double recommendedBreakerA;
    private double voltageDropV;
    private double voltageDropPercent;
    private String notes;

    public CircuitLoad(String name, CircuitKind kind) {
        this(UUID.randomUUID().toString(), name, kind, null);
    }

    public CircuitLoad(String id, String name, CircuitKind kind, String roomId) {
        this.id = Objects.requireNonNull(id, "id");
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

    public void addDeviceId(String deviceId) {
        if (deviceId != null && !deviceId.isBlank() && !deviceIds.contains(deviceId)) {
            deviceIds.add(deviceId);
        }
    }

    public void addDeviceIds(List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            addDeviceId(id);
        }
    }

    public double connectedLoadW() {
        return connectedLoadW;
    }

    public void setConnectedLoadW(double connectedLoadW) {
        this.connectedLoadW = Math.max(0, connectedLoadW);
    }

    public double designCurrentA() {
        return designCurrentA;
    }

    public void setDesignCurrentA(double designCurrentA) {
        this.designCurrentA = Math.max(0, designCurrentA);
    }

    public double diversityFactor() {
        return diversityFactor;
    }

    public void setDiversityFactor(double diversityFactor) {
        this.diversityFactor = Math.max(0, diversityFactor);
    }

    public double afterDiversityLoadW() {
        return afterDiversityLoadW;
    }

    public void setAfterDiversityLoadW(double afterDiversityLoadW) {
        this.afterDiversityLoadW = Math.max(0, afterDiversityLoadW);
    }

    public double afterDiversityCurrentA() {
        return afterDiversityCurrentA;
    }

    public void setAfterDiversityCurrentA(double afterDiversityCurrentA) {
        this.afterDiversityCurrentA = Math.max(0, afterDiversityCurrentA);
    }

    public double estimatedLengthM() {
        return estimatedLengthM;
    }

    public void setEstimatedLengthM(double estimatedLengthM) {
        this.estimatedLengthM = Math.max(0, estimatedLengthM);
    }

    public String recommendedCableId() {
        return recommendedCableId;
    }

    public void setRecommendedCableId(String recommendedCableId) {
        this.recommendedCableId = recommendedCableId;
    }

    public String recommendedCableSize() {
        return recommendedCableSize;
    }

    public void setRecommendedCableSize(String recommendedCableSize) {
        this.recommendedCableSize = recommendedCableSize;
    }

    public double recommendedBreakerA() {
        return recommendedBreakerA;
    }

    public void setRecommendedBreakerA(double recommendedBreakerA) {
        this.recommendedBreakerA = Math.max(0, recommendedBreakerA);
    }

    public double voltageDropV() {
        return voltageDropV;
    }

    public void setVoltageDropV(double voltageDropV) {
        this.voltageDropV = Math.max(0, voltageDropV);
    }

    public double voltageDropPercent() {
        return voltageDropPercent;
    }

    public void setVoltageDropPercent(double voltageDropPercent) {
        this.voltageDropPercent = Math.max(0, voltageDropPercent);
    }

    public String notes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return name + " [" + kind + ", " + connectedLoadW + " W]";
    }
}
