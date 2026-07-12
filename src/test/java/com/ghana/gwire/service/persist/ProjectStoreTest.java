package com.ghana.gwire.service.persist;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.domain.project.ProjectSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStoreTest {

    @TempDir
    Path temp;

    @Test
    void roundTripPreservesGeometryAndDevices() throws Exception {
        Project original = new Project("Test house");
        original.settings().setHouseType("Bungalow");
        original.settings().setSupplyType(ProjectSettings.SupplyType.SINGLE_PHASE_230V);

        Wall wall = new Wall(new Vec2(0, 0), new Vec2(4000, 0));
        original.floorPlan().addWall(wall);
        original.floorPlan().addRoom(new Room("Living", 0, 0, 4000, 3000));
        original.floorPlan().addOpening(new Opening(wall.id(), OpeningType.DOOR, 0.4, 900));
        PlacedDevice sock = new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 500);
        sock.setNameOverride("Twin socket");
        sock.setRoomId(original.floorPlan().rooms().get(0).id());
        original.floorPlan().addDevice(sock);
        original.floorPlan().setBackground(new BackgroundImage(
                "/tmp/plans/sample.png", "sample.png", 12.5
        ));
        original.floorPlan().setGridMm(250);

        Path file = temp.resolve("house.gwire");
        ProjectStore store = new ProjectStore();
        store.save(original, file);
        assertTrue(file.toFile().exists());

        Project loaded = store.load(file);
        assertEquals(original.id(), loaded.id());
        assertEquals("Test house", loaded.name());
        assertEquals("Bungalow", loaded.settings().houseType());
        assertEquals(1, loaded.floorPlan().walls().size());
        assertEquals(1, loaded.floorPlan().rooms().size());
        assertEquals("Living", loaded.floorPlan().rooms().get(0).name());
        assertEquals(1, loaded.floorPlan().openings().size());
        assertEquals(OpeningType.DOOR, loaded.floorPlan().openings().get(0).type());
        assertEquals(1, loaded.floorPlan().devices().size());
        assertEquals("SOCK-13A-2G", loaded.floorPlan().devices().get(0).componentId());
        assertEquals("Twin socket", loaded.floorPlan().devices().get(0).nameOverride());
        assertNotNull(loaded.floorPlan().background());
        assertEquals("/tmp/plans/sample.png", loaded.floorPlan().background().sourcePath());
        assertEquals(12.5, loaded.floorPlan().background().mmPerPixel(), 1e-9);
        assertEquals(250, loaded.floorPlan().gridMm(), 1e-9);
    }
}
