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

    public DrawingToolbar() {
        root = new HBox(6);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6, 10, 6, 10));
        root.getStyleClass().add("drawing-toolbar");

        for (DrawTool tool : DrawTool.values()) {
            ToggleButton btn = new ToggleButton(tool.label());
            btn.setToggleGroup(group);
            btn.setUserData(tool);
            btn.getStyleClass().add("tool-button");
            btn.setTooltip(new Tooltip(tooltipFor(tool)));
            btn.setOnAction(e -> {
                if (btn.isSelected()) {
                    toolListener.accept(tool);
                }
            });
            buttons.put(tool, btn);
            root.getChildren().add(btn);
        }
        buttons.get(DrawTool.SELECT).setSelected(true);

        root.getChildren().add(new Separator(Orientation.VERTICAL));

        Button importBtn = actionButton("Import plan…", "Import image or PDF as background");
        importBtn.setOnAction(e -> importAction.run());
        Button clearBg = actionButton("Clear bg", "Remove imported background");
        clearBg.setOnAction(e -> clearBgAction.run());
        Button fit = actionButton("Fit", "Fit plan to window");
        fit.setOnAction(e -> fitAction.run());

        root.getChildren().addAll(importBtn, clearBg, fit);
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

    public void selectTool(DrawTool tool) {
        ToggleButton btn = buttons.get(tool);
        if (btn != null) {
            btn.setSelected(true);
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
            case SELECT -> "Select elements (Delete to remove)";
            case PAN -> "Pan the canvas (or middle-mouse)";
            case WALL -> "Draw wall: click start, click end";
            case ROOM -> "Draw room: drag a rectangle";
            case DOOR -> "Place door on a wall";
            case WINDOW -> "Place window on a wall";
            case PLACE_DEVICE -> "Place library component (pick from symbol library)";
        };
    }
}
