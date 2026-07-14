package com.ghana.gwire.service.persist;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.electrical.ConsumerUnit;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStoreFormat13Test {

    @TempDir
    Path temp;

    @Test
    void roundTripCircuitsConsumerUnitChecklistAndHeights() throws Exception {
        Project original = new Project("Format 1.3 house");
        Room living = new Room("Living", 0, 0, 4000, 3000);
        original.floorPlan().addRoom(living);
        PlacedDevice sock = new PlacedDevice("SOCK-13A-2G", "socket_13a_2g", 500, 500);
        sock.setRoomId(living.id());
        sock.setMountingHeightMm(300);
        original.floorPlan().addDevice(sock);

        Circuit lighting = new Circuit("c1", "Lighting Living", CircuitKind.LIGHTING, living.id());
        lighting.addDeviceId(sock.id());
        lighting.setWayNumber(1);
        lighting.setBreakerA(10);
        lighting.setCableSize("1.5 mm2");
        lighting.setEstimatedLengthM(18.5);
        original.setCircuits(java.util.List.of(lighting));
        sock.setCircuitId(lighting.id());

        ConsumerUnit cu = new ConsumerUnit("cu1", "Main CU", 12, 60, "RCCB 63 A 30 mA");
        cu.assignCircuitsInOrder(original.circuits());
        original.setConsumerUnit(cu);
        original.checklistReview().setReviewed("NO_RCD", true, "Will add RCD on site");

        Path file = temp.resolve("house-1.3.gwire");
        ProjectStore store = new ProjectStore();
        store.save(original, file);

        String json = Files.readString(file);
        assertTrue(json.contains("\"formatVersion\" : \"1.3\"")
                || json.contains("\"formatVersion\": \"1.3\"")
                || json.contains("\"formatVersion\": \"1.3\""));
        assertTrue(json.contains("circuits"));
        assertTrue(json.contains("consumerUnit"));
        assertTrue(json.contains("checklistReview"));
        assertTrue(json.contains("mountingHeightMm"));

        Project loaded = store.load(file);
        assertEquals(original.id(), loaded.id());
        assertEquals(1, loaded.circuits().size());
        Circuit c = loaded.circuits().get(0);
        assertEquals("Lighting Living", c.name());
        assertEquals(CircuitKind.LIGHTING, c.kind());
        assertEquals(10, c.breakerA(), 1e-9);
        assertEquals("1.5 mm2", c.cableSize());
        assertEquals(18.5, c.estimatedLengthM(), 1e-9);
        assertEquals(1, c.deviceIds().size());

        assertNotNull(loaded.consumerUnit());
        assertEquals(12, loaded.consumerUnit().ways());
        assertEquals(60, loaded.consumerUnit().incomerA(), 1e-9);
        assertEquals(c.id(), loaded.consumerUnit().circuitIdAtWay(0));

        assertTrue(loaded.checklistReview().isReviewed("NO_RCD"));
        assertEquals("Will add RCD on site", loaded.checklistReview().note("NO_RCD"));

        PlacedDevice d = loaded.floorPlan().devices().get(0);
        assertEquals(300, d.mountingHeightMm(), 1e-9);
        assertEquals(c.id(), d.circuitId());
    }

    @Test
    void loadLegacyWithoutCircuitsStillWorks() throws Exception {
        // Minimal 1.0-style file without circuits block
        String legacy = """
                {
                  "formatVersion": "1.0",
                  "app": "GhanaWire AI",
                  "id": "legacy-id",
                  "name": "Legacy",
                  "createdAt": "2026-01-01T00:00:00Z",
                  "modifiedAt": "2026-01-01T00:00:00Z",
                  "settings": {
                    "houseType": "Bungalow",
                    "supplyType": "SINGLE_PHASE_230V",
                    "nominalVoltageV": 230,
                    "frequencyHz": 50,
                    "standardsEdition": "L.I. 2008"
                  },
                  "floorPlan": {
                    "gridMm": 500,
                    "snapToGrid": true,
                    "walls": [],
                    "rooms": [],
                    "openings": [],
                    "devices": [],
                    "wiringRoutes": []
                  }
                }
                """;
        Path file = temp.resolve("legacy.gwire");
        Files.writeString(file, legacy);
        Project loaded = new ProjectStore().load(file);
        assertEquals("Legacy", loaded.name());
        assertTrue(loaded.circuits().isEmpty());
        // save upgrades to 1.3
        Path out = temp.resolve("upgraded.gwire");
        new ProjectStore().save(loaded, out);
        String saved = Files.readString(out);
        assertTrue(saved.contains("1.3"));
    }
}
