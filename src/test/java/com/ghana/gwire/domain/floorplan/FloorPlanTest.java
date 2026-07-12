package com.ghana.gwire.domain.floorplan;

import com.ghana.gwire.domain.geometry.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPlanTest {

    @Test
    void addWallAndHitTest() {
        FloorPlan plan = new FloorPlan();
        plan.setSnapToGrid(false);
        Wall wall = new Wall(new Vec2(0, 0), new Vec2(4000, 0));
        plan.addWall(wall);

        assertEquals(1, plan.walls().size());
        assertTrue(plan.hitWall(new Vec2(2000, 50), 100).isPresent());
        assertEquals(wall.id(), plan.hitWall(new Vec2(2000, 50), 100).get().id());
    }

    @Test
    void removeWallRemovesOpenings() {
        FloorPlan plan = new FloorPlan();
        Wall wall = new Wall(new Vec2(0, 0), new Vec2(3000, 0));
        plan.addWall(wall);
        plan.addOpening(new Opening(wall.id(), OpeningType.DOOR, 0.5, 900));
        assertEquals(1, plan.openings().size());

        plan.removeWallById(wall.id());
        assertTrue(plan.walls().isEmpty());
        assertTrue(plan.openings().isEmpty());
    }

    @Test
    void roomAreaAndHit() {
        Room room = new Room("Kitchen", 0, 0, 4000, 3000);
        assertEquals(12.0, room.areaM2(), 1e-9);
        assertTrue(room.contains(new Vec2(1000, 1000)));
    }

    @Test
    void deepCopyIsIndependent() {
        FloorPlan plan = new FloorPlan();
        plan.addWall(new Wall(new Vec2(0, 0), new Vec2(1000, 0)));
        FloorPlan copy = plan.deepCopy();
        plan.clearGeometry();
        assertEquals(0, plan.walls().size());
        assertEquals(1, copy.walls().size());
    }

    @Test
    void snapToGrid() {
        FloorPlan plan = new FloorPlan();
        plan.setGridMm(500);
        plan.setSnapToGrid(true);
        Vec2 snapped = plan.snap(new Vec2(620, 180));
        assertEquals(500, snapped.x(), 1e-9);
        assertEquals(0, snapped.y(), 1e-9);
    }
}
