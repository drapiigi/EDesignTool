package com.ghana.gwire.service.electrical;

import com.ghana.gwire.db.ComponentSeed;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.electrical.ConsumerUnit;
import com.ghana.gwire.domain.electrical.MountingHeights;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.WiringRoute;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitMaterializerTest {

    @Test
    void ensureCircuitsMaterializesWhenEmpty() {
        Project project = sampleProject();
        Map<String, ElectricalComponent> cat = catalogue();

        assertTrue(project.circuits().isEmpty());
        boolean created = CircuitMaterializer.ensureCircuits(project, cat);
        assertTrue(created);
        assertFalse(project.circuits().isEmpty());
        assertNotNull(project.consumerUnit());
        assertTrue(project.consumerUnit().ways() >= 8);

        for (Circuit c : project.circuits()) {
            assertTrue(c.wayNumber() > 0, "way assigned for " + c.name());
            for (String did : c.deviceIds()) {
                assertTrue(
                        project.floorPlan().findDevice(did).isPresent(),
                        "device " + did + " on plan"
                );
                assertEquals(c.id(), project.floorPlan().findDevice(did).get().circuitId());
            }
        }

        int count = project.circuits().size();
        // Second call is idempotent (does not rebuild)
        assertFalse(CircuitMaterializer.ensureCircuits(project, cat));
        assertEquals(count, project.circuits().size());
    }

    @Test
    void applyDefaultMountingHeights() {
        Project project = sampleProject();
        CircuitMaterializer.ensureCircuits(project, catalogue());
        boolean anyHeight = project.floorPlan().devices().stream()
                .anyMatch(d -> d.mountingHeightMm() > 0);
        assertTrue(anyHeight, "expected default mounting heights");
        for (PlacedDevice d : project.floorPlan().devices()) {
            if (d.symbolKey().startsWith("socket")) {
                assertEquals(MountingHeights.SOCKET_MM, d.mountingHeightMm(), 1e-9);
            }
        }
    }

    @Test
    void rematerializeReplacesCircuitsAndRemapsRoutes() {
        Project project = sampleProject();
        Map<String, ElectricalComponent> cat = catalogue();
        CircuitMaterializer.ensureCircuits(project, cat);
        String oldId = project.circuits().get(0).id();
        WiringRoute route = new WiringRoute(oldId, "route-label");
        project.floorPlan().addWiringRoute(route);

        CircuitMaterializer.rematerialize(project, cat);
        assertFalse(project.circuits().isEmpty());
        // Route either remapped to a known circuit id or cleared
        if (route.circuitId() != null) {
            assertTrue(project.findCircuit(route.circuitId()).isPresent());
        }
    }

    @Test
    void consumerUnitAssignsSequentialWays() {
        ConsumerUnit cu = new ConsumerUnit("Main", 8);
        Circuit a = new Circuit("Lighting Living", com.ghana.gwire.domain.calc.CircuitKind.LIGHTING);
        Circuit b = new Circuit("Sockets Living", com.ghana.gwire.domain.calc.CircuitKind.SOCKET);
        cu.assignCircuitsInOrder(List.of(a, b));
        assertEquals(a.id(), cu.circuitIdAtWay(0));
        assertEquals(b.id(), cu.circuitIdAtWay(1));
        assertEquals(1, a.wayNumber());
        assertEquals(2, b.wayNumber());
        assertEquals(null, cu.circuitIdAtWay(2));
    }

    private static Project sampleProject() {
        Project project = new Project("Materializer sample");
        Room living = new Room("Living", 0, 0, 5000, 4000);
        project.floorPlan().addRoom(living);
        PlacedDevice light = new PlacedDevice("LIGHT-LED-12W", "light_led", 1000, 1000);
        light.setRoomId(living.id());
        PlacedDevice sock = new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 2000);
        sock.setRoomId(living.id());
        PlacedDevice db = new PlacedDevice("DB-8WAY", "db_8way", 0, 0);
        db.setRoomId(living.id());
        project.floorPlan().addDevice(light);
        project.floorPlan().addDevice(sock);
        project.floorPlan().addDevice(db);
        return project;
    }

    private static Map<String, ElectricalComponent> catalogue() {
        Map<String, ElectricalComponent> map = new LinkedHashMap<>();
        for (ElectricalComponent c : ComponentSeed.starterCatalogue()) {
            map.put(c.id(), c);
        }
        return map;
    }
}
