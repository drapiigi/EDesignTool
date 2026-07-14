package com.ghana.gwire.ui.canvas;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Horizontal tool strip for floor-plan editing.
 */
public class DrawingToolbar {

    private final HBox root;
    private final ToggleGroup group = new ToggleGroup();
    private final Map<DrawTool, ToggleButton> buttons = new EnumMap<>(DrawTool.class);
    private Consumer<DrawTool> toolListener = t -> {
    };
    private Runnable importAction = () -> {
    };
    private Runnable clearBgAction = () -> {
    };
    private Runnable fitAction = () -> {
    };
    private Runnable calibrateAction = () -> {
    };
    /** Suppress listener when selecting tools programmatically (avoids setTool ↔ selectTool recursion). */
    private boolean suppressToolEvents;

    public DrawingToolbar() {
        root = new HBox(6);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6, 10, 6, 10));
        root.getStyleClass().add("drawing-toolbar");

        // Primary tools only — Place is drag-from-library; Scale is a toolbar action
        DrawTool[] primary = {
                DrawTool.SELECT, DrawTool.PAN, DrawTool.WALL, DrawTool.ROOM,
                DrawTool.DOOR, DrawTool.WINDOW
        };
        for (DrawTool tool : primary) {
            ToggleButton btn = new ToggleButton(tool.label());
            btn.setToggleGroup(group);
            btn.setUserData(tool);
            btn.getStyleClass().add("tool-button");
            btn.setTooltip(new Tooltip(tooltipFor(tool)));
            btn.setOnAction(e -> {
                if (suppressToolEvents) {
                    return;
                }
                if (btn.isSelected()) {
                    toolListener.accept(tool);
                }
            });
            buttons.put(tool, btn);
            root.getChildren().add(btn);
        }
        suppressToolEvents = true;
        try {
            buttons.get(DrawTool.SELECT).setSelected(true);
        } finally {
            suppressToolEvents = false;
        }

        root.getChildren().add(new Separator(Orientation.VERTICAL));

        Button importBtn = actionButton("Import…", "Import image or PDF as background");
        importBtn.setOnAction(e -> importAction.run());
        Button calibrate = actionButton("Scale", "Calibrate background scale (two points + known length)");
        calibrate.setOnAction(e -> calibrateAction.run());
        Button clearBg = actionButton("Clear bg", "Remove imported background");
        clearBg.setOnAction(e -> clearBgAction.run());
        Button fit = actionButton("Fit", "Fit plan to window");
        fit.setOnAction(e -> fitAction.run());

        root.getChildren().addAll(importBtn, calibrate, clearBg, fit);

        root.getChildren().add(new Separator(Orientation.VERTICAL));

        ToggleButton orthoBtn = new ToggleButton("Ortho");
        orthoBtn.setTooltip(new Tooltip("Constrain walls H/V (F8 or hold Shift)"));
        orthoBtn.getStyleClass().add("tool-button");
        ToggleButton osnapBtn = new ToggleButton("OSNAP");
        osnapBtn.setTooltip(new Tooltip("Snap to wall endpoints (F3)"));
        osnapBtn.getStyleClass().add("tool-button");
        osnapBtn.setSelected(true);
        this.orthoBtn = orthoBtn;
        this.osnapBtn = osnapBtn;
        root.getChildren().addAll(orthoBtn, osnapBtn);
    }

    private ToggleButton orthoBtn;
    private ToggleButton osnapBtn;

    /** Wire CAD toggles after canvas is created. */
    public void bindCadSettings(CadSettings cad) {
        if (cad == null || orthoBtn == null) {
            return;
        }
        orthoBtn.setSelected(cad.isOrtho());
        osnapBtn.setSelected(cad.isEndpointSnap());
        orthoBtn.setOnAction(e -> cad.setOrtho(orthoBtn.isSelected()));
        osnapBtn.setOnAction(e -> cad.setEndpointSnap(osnapBtn.isSelected()));
        cad.orthoProperty().addListener((o, a, b) -> {
            if (orthoBtn.isSelected() != b) {
                orthoBtn.setSelected(b);
            }
        });
        cad.endpointSnapProperty().addListener((o, a, b) -> {
            if (osnapBtn.isSelected() != b) {
                osnapBtn.setSelected(b);
            }
        });
    }

    public HBox getRoot() {
        return root;
    }

    public void setToolListener(Consumer<DrawTool> toolListener) {
        this.toolListener = toolListener == null ? t -> {
        } : toolListener;
    }

    public void setImportAction(Runnable importAction) {
        this.importAction = importAction == null ? () -> {
        } : importAction;
    }

    public void setClearBgAction(Runnable clearBgAction) {
        this.clearBgAction = clearBgAction == null ? () -> {
        } : clearBgAction;
    }

    public void setFitAction(Runnable fitAction) {
        this.fitAction = fitAction == null ? () -> {
        } : fitAction;
    }

    public void setCalibrateAction(Runnable calibrateAction) {
        this.calibrateAction = calibrateAction == null ? () -> {
        } : calibrateAction;
    }

    public void selectTool(DrawTool tool) {
        ToggleButton btn = buttons.get(tool);
        if (btn == null) {
            // Secondary tools (Place, Scale) — leave primary selection alone
            return;
        }
        if (btn.isSelected()) {
            return;
        }
        suppressToolEvents = true;
        try {
            btn.setSelected(true);
        } finally {
            suppressToolEvents = false;
        }
    }

    private static Button actionButton(String text, String tip) {
        Button b = new Button(text);
        b.getStyleClass().add("tool-action");
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    private static String tooltipFor(DrawTool tool) {
        return switch (tool) {
            case SELECT -> "Select elements. Drag empty space to pan the whole plan; drag a symbol to move it. "
                    + "Space/middle/right-drag also pans. Two-finger scroll pans; Ctrl+scroll zooms.";
            case PAN -> "Pan the view — walls, rooms and devices move together (also Space+drag, middle or right mouse)";
            case WALL -> "Draw wall: click start, click end. Ortho F8/Shift · Endpoint OSNAP F3";
            case ROOM -> "Draw room: drag a rectangle";
            case DOOR -> "Place door on a wall";
            case WINDOW -> "Place window on a wall";
            case PLACE_DEVICE -> "Legacy place mode — prefer drag from symbol library";
            case CALIBRATE_SCALE -> "Calibrate background scale: click two points of a known length, enter real size";
        };
    }
}
