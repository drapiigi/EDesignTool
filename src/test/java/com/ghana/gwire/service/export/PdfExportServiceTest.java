package com.ghana.gwire.service.export;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.samples.SampleProjectFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
            assertTrue(doc.getNumberOfPages() >= 6, "expected multi-page report including SLD");
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Design Report") || text.contains("GhanaWire"), text);
            // Glyph substitution should not litter the report with ?
            long q = text.chars().filter(ch -> ch == '?').count();
            assertTrue(q < 5, "too many '?' glyphs (encoding issue): count=" + q + "\n" + text);
        }
    }

    @Test
    void sampleHousePdfReadableWithoutQuestionMarks() throws Exception {
        Project project = SampleProjectFactory.createThreeBedBungalow();
        Path out = temp.resolve("sample-report.pdf");
        new PdfExportService().export(project, out);
        try (PDDocument doc = Loader.loadPDF(out.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertFalse(text.isBlank());
            long q = text.chars().filter(ch -> ch == '?').count();
            assertTrue(q < 8, "sample PDF has encoding garbage (? count=" + q + ")\n" + text.substring(0, Math.min(800, text.length())));
            assertTrue(text.toLowerCase().contains("circuit") || text.contains("BOQ") || text.contains("Bill"),
                    "expected schedule/BOQ content");
        }
    }

    @Test
    void safeTransliteratesUnicode() {
        assertTrue(PdfExportService.safe("Lighting – Kitchen").contains("Lighting - Kitchen")
                || PdfExportService.safe("Lighting – Kitchen").equals("Lighting - Kitchen"));
        assertFalse(PdfExportService.safe("1.5 mm² Twin — Earth · 13A").contains("?"));
        assertTrue(PdfExportService.safe("1.5 mm²").contains("mm2"));
    }
}
