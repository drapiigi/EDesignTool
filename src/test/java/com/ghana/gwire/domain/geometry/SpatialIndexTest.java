package com.ghana.gwire.domain.geometry;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Wall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialIndexTest {

    @Test
    void findsNearestDeviceInGrid() {
        List<PlacedDevice> devices = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            devices.add(new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", i * 1000.0, 0));
        }
        PlacedDevice target = devices.get(50);
        SpatialIndex idx = SpatialIndex.forDevices(devices);
        var hit = idx.nearestDevice(new Vec2(target.xMm() + 20, 10), 100);
        assertTrue(hit.isPresent());
        assertEquals(target.id(), hit.get().id());
    }

    @Test
    void floorPlanUsesIndexAtScale() {
        FloorPlan fp = new FloorPlan();
        for (int i = 0; i < 120; i++) {
            fp.addDevice(new PlacedDevice("LIGHT-LED-9W", "light_led", i * 800.0, i * 10.0));
            fp.addWall(new Wall(new Vec2(i * 500.0, 0), new Vec2(i * 500.0 + 400, 0)));
        }
        PlacedDevice d = fp.devices().get(77);
        assertTrue(fp.hitDevice(new Vec2(d.xMm(), d.yMm()), 50).isPresent());
        assertTrue(fp.hitWall(new Vec2(77 * 500.0 + 200, 0), 50).isPresent());
    }
}
