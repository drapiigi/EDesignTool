package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;

/**
 * Current canvas selection (at most one primary element).
 */
public final class SelectionModel {

    public enum Kind {
        NONE, WALL, ROOM, OPENING, DEVICE
    }

    private Kind kind = Kind.NONE;
    private Wall wall;
    private Room room;
    private Opening opening;
    private PlacedDevice device;

    public Kind kind() {
        return kind;
    }

    public Wall wall() {
        return wall;
    }

    public Room room() {
        return room;
    }

    public Opening opening() {
        return opening;
    }

    public PlacedDevice device() {
        return device;
    }

    public void clear() {
        kind = Kind.NONE;
        wall = null;
        room = null;
        opening = null;
        device = null;
    }

    public void selectWall(Wall wall) {
        clear();
        if (wall != null) {
            kind = Kind.WALL;
            this.wall = wall;
        }
    }

    public void selectRoom(Room room) {
        clear();
        if (room != null) {
            kind = Kind.ROOM;
            this.room = room;
        }
    }

    public void selectOpening(Opening opening) {
        clear();
        if (opening != null) {
            kind = Kind.OPENING;
            this.opening = opening;
        }
    }

    public void selectDevice(PlacedDevice device) {
        clear();
        if (device != null) {
            kind = Kind.DEVICE;
            this.device = device;
        }
    }

    public boolean isEmpty() {
        return kind == Kind.NONE;
    }

    public String summary() {
        return switch (kind) {
            case WALL -> "Wall · %.0f mm".formatted(wall.lengthMm());
            case ROOM -> "%s · %.2f m²".formatted(room.name(), room.areaM2());
            case OPENING -> "%s · %.0f mm".formatted(opening.type(), opening.widthMm());
            case DEVICE -> "Device · %s".formatted(device.displayName());
            case NONE -> "Nothing selected";
        };
    }
}
