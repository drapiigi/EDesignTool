package com.ghana.gwire.service.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckServiceTest {

    @Test
    void isNewerComparesSemver() {
        assertTrue(UpdateCheckService.isNewer("0.9.1", "0.9.0"));
        assertTrue(UpdateCheckService.isNewer("1.0.0", "0.9.0"));
        assertFalse(UpdateCheckService.isNewer("0.9.0", "0.9.0"));
        assertFalse(UpdateCheckService.isNewer("0.8.9", "0.9.0"));
        assertTrue(UpdateCheckService.isNewer("0.10.0", "0.9.9"));
    }

    @Test
    void stripSnapshot() {
        assertEquals("0.9.0", UpdateCheckService.stripSnapshot("0.9.0-SNAPSHOT"));
        assertEquals("0.9.0", UpdateCheckService.stripSnapshot("0.9.0"));
    }
}
