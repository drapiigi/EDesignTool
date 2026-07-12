package com.ghana.gwire.ai;

import com.ghana.gwire.db.ComponentSeed;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDesignServiceApplyTest {

    @Test
    void applyPlanIncreasesDeviceCount() {
        Project project = new Project("Apply test");
        project.floorPlan().addRoom(new Room("Living", 0, 0, 4000, 5000));
        project.floorPlan().addRoom(new Room("Kitchen", 4000, 0, 3000, 3000));

        int before = project.floorPlan().devices().size();
        assertEquals(0, before);

        List<ElectricalComponent> catalogue = ComponentSeed.starterCatalogue();
        AiDesignService service = new AiDesignService(
                new AiSettings(AiSettings.Provider.NONE, "", null, null, false)
        );
        AiDesignPlan plan = service.generateRulesOnly(project, catalogue);
        assertFalse(plan.isEmpty());

        int added = service.apply(project, plan, true);
        assertEquals(plan.size(), added);
        assertEquals(added, project.floorPlan().devices().size());
        assertTrue(project.floorPlan().devices().size() > before);

        // Room ids preserved on at least some devices
        long withRoom = project.floorPlan().devices().stream()
                .map(PlacedDevice::roomId)
                .filter(id -> id != null && !id.isBlank())
                .count();
        assertTrue(withRoom >= 1);

        // clearExisting replaces rather than stacks
        int second = service.apply(project, plan, true);
        assertEquals(plan.size(), second);
        assertEquals(plan.size(), project.floorPlan().devices().size());
    }

    @Test
    void applyWithoutClearAppends() {
        Project project = new Project("Append");
        project.floorPlan().addRoom(new Room("Bedroom", 0, 0, 3500, 3500));
        project.floorPlan().addDevice(new PlacedDevice("LIGHT-LED-9W", "light_led", 1000, 1000));

        AiDesignService service = new AiDesignService(
                new AiSettings(AiSettings.Provider.NONE, "", null, null, false)
        );
        AiDesignPlan plan = service.generateRulesOnly(project, ComponentSeed.starterCatalogue());
        int added = service.apply(project, plan, false);
        assertEquals(1 + added, project.floorPlan().devices().size());
    }

    @Test
    void coPilotAddSocketMentionsRoom() {
        Project project = new Project("Copilot");
        Room living = new Room("Living", 0, 0, 4000, 4000);
        project.floorPlan().addRoom(living);

        AiDesignService service = new AiDesignService(
                new AiSettings(AiSettings.Provider.NONE, "", null, null, false)
        );
        String reply = service.coPilot(project, null, "add socket in Living");
        assertTrue(reply.toLowerCase().contains("socket") || reply.toLowerCase().contains("living"));
        assertTrue(project.floorPlan().devices().size() >= 2);
    }
}
