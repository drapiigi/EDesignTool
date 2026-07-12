package com.ghana.gwire.ai.vision;

import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionFloorPlanAnalyzerTest {

    @TempDir
    Path temp;

    @Test
    void parseNormalizedRoomsAndApplyToPlan() throws Exception {
        String json = """
                {
                  "confidence": 0.82,
                  "notes": "two rooms detected",
                  "rooms": [
                    {"name":"Living","type":"living","xNorm":0.0,"yNorm":0.0,"widthNorm":0.5,"heightNorm":1.0},
                    {"name":"Kitchen","type":"kitchen","xNorm":0.5,"yNorm":0.0,"widthNorm":0.5,"heightNorm":0.6}
                  ],
                  "walls": [
                    {"x1Norm":0.5,"y1Norm":0.0,"x2Norm":0.5,"y2Norm":1.0}
                  ],
                  "openings": [
                    {"type":"DOOR","xNorm":0.5,"yNorm":0.2,"widthNorm":0.04}
                  ]
                }
                """;
        VisionFloorPlanResult result = VisionFloorPlanAnalyzer.parse(json, 1000, 800);
        assertEquals(2, result.rooms().size());
        assertEquals(1, result.walls().size());
        assertEquals(1, result.openings().size());
        assertEquals(0.82, result.confidence(), 1e-6);
        assertFalse(result.isEmpty());

        FloorPlan plan = new FloorPlan();
        BackgroundImage bg = new BackgroundImage("/tmp/plan.png", "plan.png", 10.0); // 10 mm/px
        plan.setBackground(bg);
        int n = result.applyTo(plan, bg, true, true);
        assertEquals(2, n);
        assertEquals(2, plan.rooms().size());
        assertTrue(plan.walls().size() >= 1);
        // Living: half of 1000px * 10mm = 5000mm wide
        assertEquals(5000.0, plan.rooms().get(0).widthMm(), 1.0);
    }

    @Test
    void parseStripsMarkdownFences() throws Exception {
        String raw = """
                ```json
                {"confidence":0.5,"notes":"ok","rooms":[{"name":"R1","xNorm":0.1,"yNorm":0.1,"widthNorm":0.2,"heightNorm":0.2}],"walls":[],"openings":[]}
                ```
                """;
        VisionFloorPlanResult result = VisionFloorPlanAnalyzer.parse(raw, 500, 500);
        assertEquals(1, result.rooms().size());
        assertEquals("R1", result.rooms().get(0).name());
    }

    @Test
    void offlineFallbackCreatesOneRoom() {
        VisionFloorPlanResult r = VisionFloorPlanAnalyzer.offlineFullPlanRoom(2000, 1500, "scan.pdf");
        assertEquals(1, r.rooms().size());
        assertTrue(r.notes().toLowerCase().contains("offline"));
    }

    @Test
    void imageEncoderScalesLargeImages(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("big.png");
        BufferedImage bi = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 3000, 2000);
        g.setColor(Color.BLACK);
        g.drawRect(100, 100, 2800, 1800);
        g.dispose();
        ImageIO.write(bi, "png", png.toFile());

        ImageEncoder.EncodedImage enc = ImageEncoder.encodeFile(png, 1600);
        assertEquals(3000, enc.originalWidth());
        assertEquals(2000, enc.originalHeight());
        assertTrue(enc.width() <= 1600);
        assertTrue(enc.height() <= 1600);
        assertTrue(enc.base64().length() > 100);
        assertTrue(enc.dataUrl().startsWith("data:image/"));
    }
}
