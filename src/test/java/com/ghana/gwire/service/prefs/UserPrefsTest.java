package com.ghana.gwire.service.prefs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrefsTest {

    @TempDir
    Path temp;

    @Test
    void firstRunRoundTrip() {
        Path file = temp.resolve("prefs.properties");
        UserPrefs prefs = new UserPrefs(file);
        assertFalse(prefs.isFirstRunAccepted());
        prefs.setFirstRunAccepted("0.9.0");
        UserPrefs reloaded = new UserPrefs(file);
        assertTrue(reloaded.isFirstRunAccepted());
    }
}
