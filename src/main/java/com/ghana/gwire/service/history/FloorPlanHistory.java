package com.ghana.gwire.service.history;

import com.ghana.gwire.domain.floorplan.FloorPlan;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Simple undo/redo stack for floor plan geometry (deep copies).
 */
public final class FloorPlanHistory {

    private static final int MAX = 50;

    private final Deque<FloorPlan> undo = new ArrayDeque<>();
    private final Deque<FloorPlan> redo = new ArrayDeque<>();
    private final Consumer<String> onChange;

    public FloorPlanHistory(Consumer<String> onChange) {
        this.onChange = onChange == null ? s -> {
        } : onChange;
    }

    public void push(FloorPlan current) {
        Objects.requireNonNull(current);
        undo.push(current.deepCopy());
        while (undo.size() > MAX) {
            undo.removeLast();
        }
        redo.clear();
        onChange.accept("history");
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo(FloorPlan target) {
        if (!canUndo()) {
            return;
        }
        redo.push(target.deepCopy());
        target.replaceFrom(undo.pop());
        onChange.accept("undo");
    }

    public void redo(FloorPlan target) {
        if (!canRedo()) {
            return;
        }
        undo.push(target.deepCopy());
        target.replaceFrom(redo.pop());
        onChange.accept("redo");
    }

    public void clear() {
        undo.clear();
        redo.clear();
        onChange.accept("clear");
    }
}
