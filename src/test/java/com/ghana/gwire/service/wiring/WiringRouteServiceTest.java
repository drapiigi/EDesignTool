package com.ghana.gwire.service.wiring;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiringRouteServiceTest {

    @Test
    void manhattanPathHasThreePointsWhenNotAligned() {
        List<Vec2> pts = WiringRouteService.manhattan(new Vec2(0, 0), new Vec2(1000, 500));
        assertEquals(3, pts.size());
        assertEquals(0, pts.get(0).x(), 1e-9);
        assertEquals(1000, pts.get(1).x(), 1e-9);
        assertEquals(0, pts.get(1).y(), 1e-9);
        assertEquals(500, pts.get(2).y(), 1e-9);
    }

    @Test
    void generateCreatesRoutesForLoads() {
        Project p = new Project("Wiring test");
        p.floorPlan().addRoom(new Room("Living", 0, 0, 4000, 3000));
        p.floorPlan().addDevice(new PlacedDevice("DB-6WAY", "db_6way", 100, 100));
        p.floorPlan().addDevice(new PlacedDevice("LIGHT-LED-9W", "light_led", 2000, 1500));
        p.floorPlan().addDevice(new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 400));

        int n = new WiringRouteService().generateForActiveStorey(p);
        assertTrue(n >= 1);
        assertTrue(p.floorPlan().wiringRoutes().size() >= 1);
    }
}
