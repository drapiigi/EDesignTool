package com.ghana.gwire.service.export;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExportServiceTest {

    @TempDir
    Path temp;

    @Test
    void exportsMultiPagePdf() throws Exception {
        Project project = new Project("PDF Export Test");
        project.floorPlan().addRoom(new Room("Living", 0, 0, 4000, 3000));
        project.floorPlan().addWall(new Wall(new Vec2(0, 0), new Vec2(4000, 0)));
        project.floorPlan().addDevice(new PlacedDevice("LIGHT-LED-9W", "light_led", 2000, 1500));
        project.floorPlan().addDevice(new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 400));
        project.floorPlan().addDevice(new PlacedDevice("DB-6WAY", "db_6way", 200, 200));

        Path out = temp.resolve("report.pdf");
        new PdfExportService().export(project, out);

        assertTrue(Files.isRegularFile(out));
        assertTrue(Files.size(out) > 500);

        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 5, "expected multi-page report");
        }
    }
}
