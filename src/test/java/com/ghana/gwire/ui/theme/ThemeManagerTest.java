package com.ghana.gwire.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeManagerTest {

    @Test
    void defaultThemeIsDark() {
        ThemeManager manager = new ThemeManager();
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
    }

    @Test
    void toggleSwitchesBetweenDarkAndLight() {
        ThemeManager manager = new ThemeManager();
        manager.toggle();
        assertEquals(ThemeManager.Theme.LIGHT, manager.getTheme());
        manager.toggle();
        assertEquals(ThemeManager.Theme.DARK, manager.getTheme());
    }

    @Test
    void stylesheetsExistOnClasspath() {
        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            var url = ThemeManager.class.getResource("/" + theme.stylesheet());
            assertNotNull(url, "Missing stylesheet: " + theme.stylesheet());
        }
    }
}
