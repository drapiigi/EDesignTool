package com.ghana.gwire.service.history;

import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void storeyScopedUndoDoesNotTouchOtherFloor() {
        Project project = new Project("Multi");
        BuildingStorey ground = project.activeStorey();
        BuildingStorey first = new BuildingStorey("First", 1);
        project.replaceStoreys(List.of(ground, first), 0);

        FloorPlanHistory history = new FloorPlanHistory(null);

        // Edit ground
        history.push(ground.id(), ground.floorPlan());
        ground.floorPlan().addWall(new Wall(new Vec2(0, 0), new Vec2(2000, 0)));
        assertEquals(1, ground.floorPlan().walls().size());

        // Edit first floor
        history.push(first.id(), first.floorPlan());
        first.floorPlan().addWall(new Wall(new Vec2(0, 0), new Vec2(3000, 0)));
        assertEquals(1, first.floorPlan().walls().size());

        // Undo first-floor edit only
        history.undo(project, ground.floorPlan());
        assertEquals(0, first.floorPlan().walls().size());
        assertEquals(1, ground.floorPlan().walls().size());

        // Undo ground edit
        history.undo(project, ground.floorPlan());
        assertEquals(0, ground.floorPlan().walls().size());
    }
}
