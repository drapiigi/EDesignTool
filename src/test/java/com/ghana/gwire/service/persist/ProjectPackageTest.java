package com.ghana.gwire.service.persist;

import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPackageTest {

    @TempDir
    Path temp;

    private String previousHome;

    @BeforeEach
    void setHome() throws Exception {
        previousHome = System.getProperty("user.home");
        Path home = temp.resolve("home");
        Files.createDirectories(home);
        System.setProperty("user.home", home.toString());
    }

    @AfterEach
    void restoreHome() {
        if (previousHome != null) {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void packageEmbedsMediaAndReloadsFromCache() throws Exception {
        Path png = temp.resolve("plan.png");
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", png.toFile());

        Project project = new Project("Packaged");
        project.floorPlan().setBackground(new BackgroundImage(png.toString(), "plan.png", 50));

        Path packagePath = temp.resolve("house.gwirez");
        ProjectStore store = new ProjectStore();
        store.save(project, packagePath);
        assertTrue(Files.isRegularFile(packagePath));

        // Delete original raster
        Files.delete(png);

        Project loaded = store.load(packagePath);
        assertNotNull(loaded.floorPlan().background());
        assertNotNull(loaded.floorPlan().background().embeddedRef());
        Path cachePath = Path.of(loaded.floorPlan().background().sourcePath());
        assertTrue(Files.isRegularFile(cachePath), "cache media should exist after package load");
        assertEquals("1.2", ProjectStore.FORMAT_VERSION);
    }

    @Test
    void plainGwireWritesFormat12() throws Exception {
        Project project = new Project("Plain");
        Path file = temp.resolve("plain.gwire");
        ProjectStore store = new ProjectStore();
        store.save(project, file);
        String json = Files.readString(file);
        assertTrue(json.contains("\"formatVersion\" : \"1.2\"")
                || json.contains("\"formatVersion\": \"1.2\"")
                || json.contains("\"formatVersion\":\"1.2\""));
    }
}
