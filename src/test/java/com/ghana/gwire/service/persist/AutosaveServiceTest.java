package com.ghana.gwire.service.persist;

import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutosaveServiceTest {

    @TempDir
    Path temp;

    private String previousHome;
    private Path fakeHome;

    @BeforeEach
    void setHome() throws Exception {
        previousHome = System.getProperty("user.home");
        fakeHome = temp.resolve("home");
        Files.createDirectories(fakeHome);
        System.setProperty("user.home", fakeHome.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousHome != null) {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void autosaveAndDeleteMultiStorey() throws Exception {
        Project p = new Project("Two floors");
        p.floorPlan().addRoom(new Room("G", 0, 0, 3000, 3000));
        BuildingStorey upper = new BuildingStorey("First", 1);
        upper.floorPlan().addRoom(new Room("U", 0, 0, 2000, 2000));
        p.replaceStoreys(List.of(p.activeStorey(), upper), 0);

        AutosaveService svc = new AutosaveService();
        svc.autosave(p);
        Path path = svc.autosavePath(p.id());
        assertTrue(Files.isRegularFile(path));

        Project loaded = svc.loadAutosave(path);
        assertEquals(2, loaded.storeys().size());
        assertEquals(1, loaded.storeys().get(0).floorPlan().rooms().size());

        svc.deleteAutosave(p.id());
        assertFalse(Files.exists(path));
    }

    @Test
    void recoveryOnlyWhenUnclean() throws Exception {
        Project p = new Project("Crashy");
        AutosaveService svc = new AutosaveService();
        svc.autosave(p);
        svc.writeCleanExitMarker();
        assertTrue(svc.recoveryCandidate().isEmpty());

        svc.clearCleanExitMarker();
        assertTrue(svc.recoveryCandidate().isPresent());
    }
}
