package com.ghana.gwire.ui.canvas;

public enum DrawTool {
    SELECT("Select"),
    PAN("Pan"),
    WALL("Wall"),
    ROOM("Room"),
    DOOR("Door"),
    WINDOW("Window"),
    /** Place selected catalogue component (set via symbol library). */
    PLACE_DEVICE("Place");

    private final String label;

    DrawTool(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
