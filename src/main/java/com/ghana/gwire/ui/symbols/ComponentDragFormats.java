package com.ghana.gwire.ui.symbols;

import javafx.scene.input.DataFormat;

/**
 * Drag-and-drop payload formats for placing catalogue components on the canvas.
 */
public final class ComponentDragFormats {

    /** Component catalogue id (e.g. {@code SOCK-13A-1G}). */
    public static final DataFormat COMPONENT_ID =
            new DataFormat("application/x-gwire-component-id");

    private ComponentDragFormats() {
    }
}
