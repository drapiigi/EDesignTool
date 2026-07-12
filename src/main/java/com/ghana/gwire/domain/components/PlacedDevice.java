package com.ghana.gwire.domain.components;

import com.ghana.gwire.domain.geometry.Vec2;

import java.util.Objects;
import java.util.UUID;

/**
 * Instance of a catalogue component placed on the floor plan (mm coordinates).
 */
public final class PlacedDevice {

    private final String id;
    private final String componentId;
    private final String symbolKey;
    private String nameOverride;
    private double xMm;
    private double yMm;
    private double rotationDeg;
    private String roomId;

    public PlacedDevice(String componentId, String symbolKey, double xMm, double yMm) {
        this(UUID.randomUUID().toString(), componentId, symbolKey, null, xMm, yMm, 0, null);
    }

    public PlacedDevice(
            String id,
            String componentId,
            String symbolKey,
            String nameOverride,
            double xMm,
            double yMm,
            double rotationDeg,
            String roomId
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.componentId = Objects.requireNonNull(componentId, "componentId");
        this.symbolKey = Objects.requireNonNull(symbolKey, "symbolKey");
        this.nameOverride = nameOverride;
        this.xMm = xMm;
        this.yMm = yMm;
        this.rotationDeg = rotationDeg;
        this.roomId = roomId;
    }

    public String id() {
        return id;
    }

    public String componentId() {
        return componentId;
    }

    public String symbolKey() {
        return symbolKey;
    }

    public String nameOverride() {
        return nameOverride;
    }

    public void setNameOverride(String nameOverride) {
        this.nameOverride = nameOverride;
    }

    public double xMm() {
        return xMm;
    }

    public double yMm() {
        return yMm;
    }

    public void setPosition(double xMm, double yMm) {
        this.xMm = xMm;
        this.yMm = yMm;
    }

    public double rotationDeg() {
        return rotationDeg;
    }

    public void setRotationDeg(double rotationDeg) {
        this.rotationDeg = rotationDeg;
    }

    public String roomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Vec2 position() {
        return new Vec2(xMm, yMm);
    }

    public double distanceTo(Vec2 p) {
        return position().distanceTo(p);
    }

    /** Display name: override if set, else symbol key. */
    public String displayName() {
        return nameOverride == null || nameOverride.isBlank() ? symbolKey : nameOverride;
    }

    public PlacedDevice copy() {
        return new PlacedDevice(id, componentId, symbolKey, nameOverride, xMm, yMm, rotationDeg, roomId);
    }
}
