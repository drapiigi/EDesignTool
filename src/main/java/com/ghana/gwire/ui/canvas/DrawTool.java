package com.ghana.gwire.ui.canvas;

public enum DrawTool {
    SELECT("Select"),
    PAN("Pan"),
    WALL("Wall"),
    ROOM("Room"),
    DOOR("Door"),
    WINDOW("Window");

    private final String label;

    DrawTool(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
