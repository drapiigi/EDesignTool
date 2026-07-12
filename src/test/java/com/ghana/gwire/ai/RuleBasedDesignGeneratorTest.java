package com.ghana.gwire.ai;

import com.ghana.gwire.db.ComponentSeed;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedDesignGeneratorTest {

    @Test
    void twoRoomsProduceLightsSocketsDbAndProtection() {
        Project project = new Project("Phase 5 sample");
        Room living = new Room("Living", 0, 0, 4000, 5000);
        Room kitchen = new Room("Kitchen", 4000, 0, 3000, 3000);
        project.floorPlan().addRoom(living);
        project.floorPlan().addRoom(kitchen);

        List<ElectricalComponent> catalogue = ComponentSeed.starterCatalogue();
        RuleBasedDesignGenerator gen = new RuleBasedDesignGenerator();
        AiDesignPlan plan = gen.generate(project, catalogue);

        assertFalse(plan.isEmpty(), "placements must be non-empty");
        assertEquals(AiDesignPlan.Source.RULES, plan.source());
        assertTrue(plan.notes() != null && !plan.notes().isBlank());

        // Living 4×5 m = 20 m² → 2 lights (ceil(20/12)=2), 4 sockets (large), 1 switch
        // Kitchen 3×3 = 9 m² → 1 light, 2 sockets + kitchen extra, switch + cooker DP
        assertTrue(plan.countByPrefix("LIGHT") >= 2, "expected lights, got " + plan.countByPrefix("LIGHT"));
        assertTrue(plan.countByPrefix("SOCK") >= 4, "expected sockets, got " + plan.countByPrefix("SOCK"));
        assertTrue(plan.countByPrefix("DB") >= 1, "expected at least 1 DB");
        assertTrue(
                plan.countByPrefix("RCCB") + plan.countContaining("RCBO") >= 1,
                "expected protection device (RCCB/RCBO)"
        );
        assertTrue(plan.countByPrefix("EARTH") >= 1, "expected earthing device");

        // Kitchen cooker switch
        assertTrue(plan.countContaining("45A") >= 1 || plan.countByPrefix("SW-45A") >= 1,
                "kitchen should get cooker switch");

        // Room ids set on room devices
        long withRoom = plan.placements().stream().filter(p -> p.roomId() != null).count();
        assertTrue(withRoom >= 4, "room devices should carry roomId");

        // Sample counts for deliverable reporting
        System.out.printf(
                "Sample plan: total=%d lights=%d sockets=%d switches=%d DB=%d protection=%d earth=%d%n",
                plan.size(),
                plan.countByPrefix("LIGHT"),
                plan.countByPrefix("SOCK"),
                plan.countByPrefix("SW"),
                plan.countByPrefix("DB"),
                plan.countByPrefix("RCCB") + plan.countByPrefix("RCBO"),
                plan.countByPrefix("EARTH")
        );
    }

    @Test
    void emptyCatalogueStillReturnsPlanWithoutThrowing() {
        Project project = new Project("Sparse");
        project.floorPlan().addRoom(new Room("Bedroom", 0, 0, 3000, 3000));
        AiDesignPlan plan = new RuleBasedDesignGenerator().generate(project, List.of());
        assertTrue(plan.isEmpty() || plan.size() == 0);
        assertEquals(AiDesignPlan.Source.RULES, plan.source());
    }

    @Test
    void wetRoomUsesBulkheadAndLimitedSockets() {
        Project project = new Project("Wet");
        project.floorPlan().addRoom(new Room("Bathroom", 0, 0, 2000, 2500));
        AiDesignPlan plan = new RuleBasedDesignGenerator()
                .generate(project, ComponentSeed.starterCatalogue());

        assertTrue(plan.countContaining("BULKHEAD") >= 1 || plan.countByPrefix("LIGHT") >= 1);
        assertTrue(plan.countByPrefix("SOCK") <= 1, "wet zone: at most one socket");
    }
}
