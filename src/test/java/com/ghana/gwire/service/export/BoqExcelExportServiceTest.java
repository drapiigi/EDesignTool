package com.ghana.gwire.service.export;

import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BoqExcelExportServiceTest {

    @TempDir
    Path temp;

    @Test
    void exportsXlsxWithBoqSheet() throws Exception {
        Project project = new Project("Excel BOQ Test");
        project.floorPlan().addRoom(new Room("Living", 0, 0, 4000, 3000));
        project.floorPlan().addDevice(new PlacedDevice("LIGHT-LED-9W", "light_led", 2000, 1500));
        project.floorPlan().addDevice(new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 400));
        project.floorPlan().addDevice(new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 800, 400));

        Path out = temp.resolve("boq.xlsx");
        new BoqExcelExportService().export(project, out, false);

        assertTrue(Files.isRegularFile(out));
        assertTrue(Files.size(out) > 500);

        try (Workbook wb = WorkbookFactory.create(out.toFile())) {
            assertTrue(wb.getNumberOfSheets() >= 1);
            assertTrue(wb.getSheetName(0).toLowerCase().contains("boq")
                    || wb.getSheetAt(0).getPhysicalNumberOfRows() > 3);
        }
    }
}
