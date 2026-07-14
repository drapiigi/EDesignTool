package com.ghana.gwire.ai;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Non-destructive AI design preview (Phase 15). Holds a plan and which placement
 * indices will be applied on accept. Generators are not modified.
 */
public final class AiPreviewSession {

    private final AiDesignPlan plan;
    private final boolean clearExistingDevices;
    private final BitSet selected;

    public AiPreviewSession(AiDesignPlan plan, boolean clearExistingDevices) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.clearExistingDevices = clearExistingDevices;
        this.selected = new BitSet(plan.size());
        // Default: all selected
        selected.set(0, plan.size());
    }

    public AiDesignPlan plan() {
        return plan;
    }

    public boolean clearExistingDevices() {
        return clearExistingDevices;
    }

    public int size() {
        return plan.size();
    }

    public boolean isSelected(int index) {
        return index >= 0 && index < plan.size() && selected.get(index);
    }

    public void setSelected(int index, boolean value) {
        if (index >= 0 && index < plan.size()) {
            selected.set(index, value);
        }
    }

    public void toggle(int index) {
        if (index >= 0 && index < plan.size()) {
            selected.flip(index);
        }
    }

    public void selectAll() {
        selected.set(0, plan.size());
    }

    public void selectNone() {
        selected.clear();
    }

    public int selectedCount() {
        return selected.cardinality();
    }

    public List<DesignPlacement> selectedPlacements() {
        List<DesignPlacement> out = new ArrayList<>();
        List<DesignPlacement> all = plan.placements();
        for (int i = 0; i < all.size(); i++) {
            if (selected.get(i)) {
                out.add(all.get(i));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Plan containing only selected placements (same source/notes). */
    public AiDesignPlan toFilteredPlan() {
        return new AiDesignPlan(plan.source(), plan.notes(), selectedPlacements(), plan.providerDetail());
    }
}
