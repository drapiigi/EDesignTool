package com.ghana.gwire.samples;

import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.persist.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleProjectFactoryTest {

    @TempDir
    Path temp;

    @Test
    void threeBedSampleHasExpectedStructure() {
        Project p = SampleProjectFactory.createThreeBedBungalow();
        assertEquals(SampleProjectFactory.SAMPLE_NAME, p.name());
        assertTrue(p.floorPlan().rooms().size() >= 8, "expected multi-room bungalow");
        assertTrue(p.floorPlan().devices().size() >= 20, "expected furnished sample devices");
        assertTrue(p.floorPlan().rooms().stream().anyMatch(r -> r.name().contains("Kitchen")));
        assertTrue(p.floorPlan().rooms().stream().anyMatch(r -> r.name().contains("Master")));
        assertTrue(p.floorPlan().rooms().stream().anyMatch(r -> r.name().contains("Bedroom 2")));
        assertTrue(p.floorPlan().rooms().stream().anyMatch(r -> r.name().contains("Bedroom 3")));
    }

    @Test
    void oneBedSampleHasExpectedStructure() {
        Project p = SampleProjectFactory.createOneBedBungalow();
        assertEquals(SampleProjectFactory.ONE_BED_NAME, p.name());
        assertTrue(p.floorPlan().rooms().size() >= 3, "expected at least 3 rooms");
        assertTrue(p.floorPlan().devices().size() >= 8, "expected furnished sample devices");
        assertTrue(p.floorPlan().rooms().stream().anyMatch(r -> r.name().contains("Kitchen")));
        assertTrue(p.floorPlan().rooms().stream().anyMatch(r -> r.name().contains("Bedroom")));
    }

    @Test
    void twoStoreySampleHasRoomsOnBothLevels() {
        Project p = SampleProjectFactory.createTwoStoreyHouse();
        assertEquals(SampleProjectFactory.TWO_STOREY_NAME, p.name());
        assertEquals(2, p.storeys().size());
        assertEquals(0, p.activeStoreyIndex(), "should return with ground active");

        assertTrue(p.storeys().get(0).floorPlan().rooms().size() >= 1, "ground needs rooms");
        assertTrue(p.storeys().get(1).floorPlan().rooms().size() >= 1, "first floor needs rooms");
        assertTrue(p.storeys().get(0).floorPlan().devices().size() >= 1, "ground needs devices");
    }

    @Test
    void sampleRoundTripsThroughProjectStore() throws Exception {
        Project p = SampleProjectFactory.createThreeBedBungalow();
        Path file = temp.resolve("sample.gwire");
        new ProjectStore().save(p, file);
        assertTrue(Files.size(file) > 500);
        Project loaded = new ProjectStore().load(file);
        assertEquals(p.floorPlan().rooms().size(), loaded.floorPlan().rooms().size());
        assertEquals(p.floorPlan().devices().size(), loaded.floorPlan().devices().size());
    }

    @Test
    void twoStoreyRoundTripsThroughProjectStore() throws Exception {
        Project p = SampleProjectFactory.createTwoStoreyHouse();
        Path file = temp.resolve("two-storey.gwire");
        new ProjectStore().save(p, file);
        Project loaded = new ProjectStore().load(file);
        assertEquals(2, loaded.storeys().size());
        assertEquals(p.totalRoomCount(), loaded.totalRoomCount());
        assertEquals(p.totalDeviceCount(), loaded.totalDeviceCount());
    }
}
