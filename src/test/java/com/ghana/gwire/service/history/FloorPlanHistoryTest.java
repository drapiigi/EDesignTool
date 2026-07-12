package com.ghana.gwire.service.history;

import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPlanHistoryTest {

    @Test
    void undoRestoresPreviousState() {
        FloorPlan plan = new FloorPlan();
        FloorPlanHistory history = new FloorPlanHistory(null);

        history.push(plan);
        plan.addWall(new Wall(new Vec2(0, 0), new Vec2(1000, 0)));
        assertEquals(1, plan.walls().size());
        assertTrue(history.canUndo());

        history.undo(plan);
        assertEquals(0, plan.walls().size());
        assertTrue(history.canRedo());

        history.redo(plan);
        assertEquals(1, plan.walls().size());
        assertFalse(history.canRedo());
    }
}
