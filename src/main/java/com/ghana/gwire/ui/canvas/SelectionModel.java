package com.ghana.gwire.ui.canvas;

import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;

/**
 * Current canvas selection (at most one primary element in Phase 2).
 */
public final class SelectionModel {

    public enum Kind {
        NONE, WALL, ROOM, OPENING
    }

    private Kind kind = Kind.NONE;
    private Wall wall;
    private Room room;
    private Opening opening;

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

    public void clear() {
        kind = Kind.NONE;
        wall = null;
        room = null;
        opening = null;
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

    public boolean isEmpty() {
        return kind == Kind.NONE;
    }

    public String summary() {
        return switch (kind) {
            case WALL -> "Wall · %.0f mm".formatted(wall.lengthMm());
            case ROOM -> "%s · %.2f m²".formatted(room.name(), room.areaM2());
            case OPENING -> "%s · %.0f mm".formatted(opening.type(), opening.widthMm());
            case NONE -> "Nothing selected";
        };
    }
}
