package com.ghana.gwire.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPreviewSessionTest {

    @Test
    void defaultsAllSelectedAndFilters() {
        DesignPlacement a = new DesignPlacement("SOCK-13A-2G", "socket_13a_2g", "S1", 100, 100, null);
        DesignPlacement b = new DesignPlacement("LIGHT-LED-9W", "light_led", "L1", 200, 200, null);
        AiDesignPlan plan = new AiDesignPlan(AiDesignPlan.Source.RULES, "test", List.of(a, b), "unit");
        AiPreviewSession session = new AiPreviewSession(plan, true);

        assertEquals(2, session.selectedCount());
        session.setSelected(0, false);
        assertEquals(1, session.selectedCount());
        assertFalse(session.isSelected(0));
        assertTrue(session.isSelected(1));

        AiDesignPlan filtered = session.toFilteredPlan();
        assertEquals(1, filtered.size());
        assertEquals("LIGHT-LED-9W", filtered.placements().get(0).componentId());
        assertTrue(session.clearExistingDevices());
    }

    @Test
    void selectNoneAndAll() {
        DesignPlacement a = new DesignPlacement("SOCK-13A-2G", "socket_13a_2g", "S1", 0, 0, null);
        AiPreviewSession session = new AiPreviewSession(
                new AiDesignPlan(AiDesignPlan.Source.RULES, "", List.of(a), ""), false);
        session.selectNone();
        assertEquals(0, session.selectedCount());
        session.selectAll();
        assertEquals(1, session.selectedCount());
        session.toggle(0);
        assertEquals(0, session.selectedCount());
    }
}
