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
    void sampleRoundTripsThroughProjectStore() throws Exception {
        Project p = SampleProjectFactory.createThreeBedBungalow();
        Path file = temp.resolve("sample.gwire");
        new ProjectStore().save(p, file);
        assertTrue(Files.size(file) > 500);
        Project loaded = new ProjectStore().load(file);
        assertEquals(p.floorPlan().rooms().size(), loaded.floorPlan().rooms().size());
        assertEquals(p.floorPlan().devices().size(), loaded.floorPlan().devices().size());
    }
}
