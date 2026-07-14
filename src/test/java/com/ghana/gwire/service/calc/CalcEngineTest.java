package com.ghana.gwire.service.calc;

import com.ghana.gwire.db.ComponentSeed;
import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalcEngineTest {

    @Test
    void syntheticRoomProducesReportWithCablesAndIssues() {
        Project project = new Project("Phase 4 sample");
        Room living = new Room("Living", 0, 0, 5000, 4000);
        project.floorPlan().addRoom(living);

        // 2 lights, 2 sockets, 1 DB
        PlacedDevice light1 = new PlacedDevice("LIGHT-LED-12W", "light_led", 1000, 1000);
        light1.setRoomId(living.id());
        PlacedDevice light2 = new PlacedDevice("LIGHT-LED-12W", "light_led", 3000, 1000);
        light2.setRoomId(living.id());
        PlacedDevice sock1 = new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 2000);
        sock1.setRoomId(living.id());
        PlacedDevice sock2 = new PlacedDevice("SOCK-13A-1G", "socket_13a", 4500, 2000);
        sock2.setRoomId(living.id());
        PlacedDevice db = new PlacedDevice("DB-8WAY", "db_8way", 0, 0);
        db.setRoomId(living.id());

        project.floorPlan().addDevice(light1);
        project.floorPlan().addDevice(light2);
        project.floorPlan().addDevice(sock1);
        project.floorPlan().addDevice(sock2);
        project.floorPlan().addDevice(db);

        List<ElectricalComponent> catalogue = ComponentSeed.starterCatalogue();
        CalcEngine engine = new CalcEngine();
        DesignReport report = engine.calculate(project, catalogue);

        assertNotNull(report);
        assertEquals("Phase 4 sample", report.projectName());
        assertFalse(report.circuits().isEmpty(), "expected circuits");
        assertTrue(
                report.circuits().stream().anyMatch(c -> c.kind() == CircuitKind.LIGHTING),
                "expected lighting circuit"
        );
        assertTrue(
                report.circuits().stream().anyMatch(c -> c.kind() == CircuitKind.SOCKET),
                "expected socket circuit"
        );

        // Connected loads: 2×12 W lights + twin 360 + single 180 = 564 W
        assertTrue(report.totalConnectedLoadW() > 0);
        assertEquals(24 + 360 + 180, report.totalConnectedLoadW(), 1e-6);

        // Cable recommendations present
        boolean anyCable = report.circuits().stream()
                .anyMatch(c -> c.recommendedCableId() != null && !c.recommendedCableId().isBlank());
        assertTrue(anyCable, "expected at least one cable recommendation");
        assertFalse(report.cableBoq().isEmpty(), "expected cable BOQ lines");

        // Issues list non-null (NO_RCD, NO_EARTH, MCB_RECOMMEND INFO, etc.)
        assertNotNull(report.issues());
        assertFalse(report.issues().isEmpty());
        assertTrue(report.issues().stream().anyMatch(i -> "NO_RCD".equals(i.code())));
        assertTrue(report.warningCount() >= 1);

        // Lengths use DB geometry (not pure defaults when DB present)
        for (CircuitLoad c : report.circuits()) {
            assertTrue(c.estimatedLengthM() > 0, "length for " + c.name());
        }

        project.setLastReport(report);
        assertEquals(report, project.lastReport());

        // Phase 14: calc materializes persistent circuits on the project
        assertFalse(project.circuits().isEmpty(), "expected persistent circuits after calc");
        assertNotNull(project.consumerUnit());
        // Second pass prefers persistent circuits and stays consistent
        DesignReport again = engine.calculate(project, catalogue);
        assertEquals(report.totalConnectedLoadW(), again.totalConnectedLoadW(), 1e-6);
        assertEquals(report.circuits().size(), again.circuits().size());
    }

    @Test
    void nullLibraryAndEmptyDevicesSafe() {
        Project project = new Project("Empty");
        CalcEngine engine = new CalcEngine();
        DesignReport report = engine.calculate(project, (com.ghana.gwire.db.ComponentLibraryService) null);
        assertNotNull(report);
        assertTrue(report.circuits().isEmpty());
        assertEquals(0, report.totalConnectedLoadW(), 1e-9);
    }

    @Test
    void circuitBuilderGroupsByRoom() {
        Project project = new Project("Rooms");
        Room a = new Room("Bedroom", 0, 0, 3000, 3000);
        Room b = new Room("Kitchen", 3000, 0, 3000, 3000);
        project.floorPlan().addRoom(a);
        project.floorPlan().addRoom(b);

        PlacedDevice l1 = new PlacedDevice("LIGHT-LED-12W", "light_led", 500, 500);
        l1.setRoomId(a.id());
        PlacedDevice l2 = new PlacedDevice("LIGHT-FLUOR-36W", "light_fluorescent", 3500, 500);
        l2.setRoomId(b.id());
        project.floorPlan().addDevice(l1);
        project.floorPlan().addDevice(l2);

        List<ElectricalComponent> catalogue = ComponentSeed.starterCatalogue();
        DesignReport report = new CalcEngine().calculate(project, catalogue);

        long lightingCircuits = report.circuits().stream()
                .filter(c -> c.kind() == CircuitKind.LIGHTING)
                .count();
        assertEquals(2, lightingCircuits);
        assertTrue(report.circuits().stream().anyMatch(c -> c.name().contains("Bedroom")));
        assertTrue(report.circuits().stream().anyMatch(c -> c.name().contains("Kitchen")));
    }

    @Test
    void loadTablesUsesCataloguePowerW() {
        ElectricalComponent led = ComponentSeed.starterCatalogue().stream()
                .filter(c -> "LIGHT-LED-12W".equals(c.id()))
                .findFirst()
                .orElseThrow();
        PlacedDevice d = new PlacedDevice("LIGHT-LED-12W", "light_led", 0, 0);
        assertEquals(12.0, LoadTables.assumedPowerW(led, d), 1e-9);
    }

    @Test
    void loadTablesTwinSocketIsDouble() {
        ElectricalComponent twin = ComponentSeed.starterCatalogue().stream()
                .filter(c -> "SOCK-13A-2G".equals(c.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(360.0, LoadTables.assumedPowerW(twin, null), 1e-9);
    }

    @Test
    void designReportErrorCount() {
        DesignReport r = new DesignReport();
        r.addIssue(com.ghana.gwire.domain.calc.ValidationIssue.of(
                Severity.ERROR, "X", "err"));
        r.addIssue(com.ghana.gwire.domain.calc.ValidationIssue.of(
                Severity.WARNING, "Y", "warn"));
        assertEquals(1, r.errorCount());
        assertEquals(1, r.warningCount());
        assertTrue(r.hasErrors());
    }
}
