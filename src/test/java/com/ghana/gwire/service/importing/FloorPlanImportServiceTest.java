package com.ghana.gwire.service.importing;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPlanImportServiceTest {

    @TempDir
    Path temp;

    @BeforeAll
    static void initJavaFx() throws Exception {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
        } catch (IllegalStateException alreadyStarted) {
            // Toolkit already running in this JVM
        }
    }

    @Test
    void importsPngImage() throws Exception {
        Path png = temp.resolve("plan.png");
        BufferedImage bi = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.BLACK);
        g.drawRect(10, 10, 180, 80);
        g.dispose();
        ImageIO.write(bi, "png", png.toFile());

        FloorPlanImportService service = new FloorPlanImportService();
        assertTrue(service.isSupported(png));
        ImportedRaster raster = service.importFile(png);
        assertEquals(200, raster.pixelWidth());
        assertEquals(100, raster.pixelHeight());
        assertTrue(raster.suggestedMmPerPixel() > 0);
        assertEquals(200, (int) Math.round(raster.image().getWidth()));
    }

    @Test
    void supportsPdfExtension() {
        FloorPlanImportService service = new FloorPlanImportService();
        assertTrue(service.isSupported(Path.of("plan.PDF")));
    }
}
