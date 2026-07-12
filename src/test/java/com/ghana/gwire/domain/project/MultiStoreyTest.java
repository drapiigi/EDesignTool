package com.ghana.gwire.domain.project;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.service.persist.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiStoreyTest {

    @TempDir
    Path temp;

    @Test
    void activeFloorPlanSwitchesWithStorey() {
        Project p = new Project("Multi");
        p.floorPlan().addRoom(new Room("G Living", 0, 0, 4000, 3000));
        p.addStorey("First floor", 1);
        p.setActiveStoreyIndex(1);
        p.floorPlan().addRoom(new Room("L1 Bed", 0, 0, 3500, 3500));
        p.floorPlan().addDevice(new PlacedDevice("LIGHT-LED-9W", "light_led", 1000, 1000));

        assertEquals(2, p.storeys().size());
        assertEquals(1, p.activeStoreyIndex());
        assertEquals(1, p.floorPlan().rooms().size());
        assertEquals("L1 Bed", p.floorPlan().rooms().get(0).name());

        p.setActiveStoreyIndex(0);
        assertEquals("G Living", p.floorPlan().rooms().get(0).name());
        assertEquals(2, p.totalRoomCount());
        assertEquals(1, p.totalDeviceCount());
    }

    @Test
    void multiStoreyRoundTrip() throws Exception {
        Project p = new Project("Multi save");
        p.floorPlan().addRoom(new Room("Ground", 0, 0, 4000, 3000));
        p.addStorey("First floor", 1);
        p.setActiveStoreyIndex(1);
        p.floorPlan().addRoom(new Room("Upper", 0, 0, 4000, 3000));
        p.floorPlan().addDevice(new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 500));

        Path file = temp.resolve("multi.gwire");
        new ProjectStore().save(p, file);
        Project loaded = new ProjectStore().load(file);
        assertEquals(2, loaded.storeys().size());
        assertEquals(2, loaded.totalRoomCount());
        assertEquals(1, loaded.totalDeviceCount());
        assertTrue(loaded.storeys().stream().anyMatch(s -> s.name().contains("First")));
    }
}
