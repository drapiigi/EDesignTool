package com.ghana.gwire.service.cad;

import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DxfRoundTripTest {

    @TempDir
    Path temp;

    @Test
    void exportContainsLinesAndImportParsesThem() throws Exception {
        Project p = new Project("DXF test");
        p.floorPlan().addWall(new Wall(new Vec2(0, 0), new Vec2(4000, 0)));
        p.floorPlan().addWall(new Wall(new Vec2(4000, 0), new Vec2(4000, 3000)));

        DxfExportService export = new DxfExportService();
        String dxf = export.toDxf(p);
        assertTrue(dxf.contains("LINE"));
        assertTrue(dxf.contains("4000"));

        Path file = temp.resolve("out.dxf");
        export.export(p, file);

        DxfImportService importer = new DxfImportService();
        List<Wall> walls = importer.parseWalls(java.nio.file.Files.readString(file));
        assertEquals(2, walls.size());
        assertEquals(4000, walls.get(0).lengthMm(), 1.0);
    }

    @Test
    void importAppendsToProject() throws Exception {
        Project p = new Project("Import");
        p.floorPlan().addWall(new Wall(new Vec2(0, 0), new Vec2(1000, 0)));
        String dxf = """
                0
                SECTION
                2
                ENTITIES
                0
                LINE
                8
                0
                10
                0
                20
                0
                11
                0
                21
                2000
                0
                ENDSEC
                0
                EOF
                """;
        Path file = temp.resolve("one.dxf");
        java.nio.file.Files.writeString(file, dxf);
        int n = new DxfImportService().importWalls(p, file, false);
        assertEquals(1, n);
        assertEquals(2, p.floorPlan().walls().size());
    }
}
