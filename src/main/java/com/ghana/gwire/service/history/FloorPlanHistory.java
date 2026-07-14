package com.ghana.gwire.service.history;

import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Undo/redo stack for floor-plan edits.
 *
 * <p>Phase 13a: each entry stores a {@code storeyId} so undo applies only to that
 * storey's plan (no cross-storey corruption). Legacy single-plan APIs remain for tests.
 */
public final class FloorPlanHistory {

    private static final int MAX = 50;

    private record Entry(String storeyId, FloorPlan snapshot) {
    }

    private final Deque<Entry> undo = new ArrayDeque<>();
    private final Deque<Entry> redo = new ArrayDeque<>();
    private final Consumer<String> onChange;

    public FloorPlanHistory(Consumer<String> onChange) {
        this.onChange = onChange == null ? s -> {
        } : onChange;
    }

    /**
     * Snapshot the given plan for a storey before a mutating edit.
     *
     * @param storeyId storey id (may be null for single-plan / tests)
     * @param current  live plan state <em>before</em> the edit
     */
    public void push(String storeyId, FloorPlan current) {
        Objects.requireNonNull(current);
        undo.push(new Entry(storeyId, current.deepCopy()));
        while (undo.size() > MAX) {
            undo.removeLast();
        }
        redo.clear();
        onChange.accept("history");
    }

    /** Legacy: push without storey (tests / single floor). */
    public void push(FloorPlan current) {
        push(null, current);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /**
     * Undo the last command into the matching storey of {@code project}, or into
     * {@code fallbackPlan} when the entry has no storey id.
     */
    public void undo(Project project, FloorPlan fallbackPlan) {
        if (!canUndo()) {
            return;
        }
        Entry e = undo.pop();
        FloorPlan target = resolvePlan(project, e.storeyId(), fallbackPlan);
        if (target == null) {
            return;
        }
        redo.push(new Entry(e.storeyId(), target.deepCopy()));
        target.replaceFrom(e.snapshot());
        onChange.accept("undo");
    }

    /** Legacy single-plan undo. */
    public void undo(FloorPlan target) {
        undo(null, target);
    }

    public void redo(Project project, FloorPlan fallbackPlan) {
        if (!canRedo()) {
            return;
        }
        Entry e = redo.pop();
        FloorPlan target = resolvePlan(project, e.storeyId(), fallbackPlan);
        if (target == null) {
            return;
        }
        undo.push(new Entry(e.storeyId(), target.deepCopy()));
        target.replaceFrom(e.snapshot());
        onChange.accept("redo");
    }

    public void redo(FloorPlan target) {
        redo(null, target);
    }

    public void clear() {
        undo.clear();
        redo.clear();
        onChange.accept("clear");
    }

    private static FloorPlan resolvePlan(Project project, String storeyId, FloorPlan fallback) {
        if (project != null && storeyId != null) {
            for (BuildingStorey s : project.storeys()) {
                if (storeyId.equals(s.id())) {
                    return s.floorPlan();
                }
            }
        }
        return fallback;
    }
}
