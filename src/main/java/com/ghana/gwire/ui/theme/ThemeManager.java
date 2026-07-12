package com.ghana.gwire.ui.theme;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Loads light/dark CSS themes and applies them to the active scene.
 */
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    public enum Theme {
        DARK("css/theme-dark.css", "Dark"),
        LIGHT("css/theme-light.css", "Light");

        private final String stylesheet;
        private final String label;

        Theme(String stylesheet, String label) {
            this.stylesheet = stylesheet;
            this.label = label;
        }

        public String stylesheet() {
            return stylesheet;
        }

        public String label() {
            return label;
        }
    }

    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.DARK);
    private Scene scene;

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public Theme getTheme() {
        return theme.get();
    }

    public void setTheme(Theme value) {
        theme.set(value);
        if (scene != null) {
            apply(scene);
        }
    }

    public void toggle() {
        setTheme(getTheme() == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    public void apply(Scene target) {
        this.scene = Objects.requireNonNull(target, "scene");
        String path = "/" + getTheme().stylesheet();
        var url = ThemeManager.class.getResource(path);
        if (url == null) {
            log.error("Theme stylesheet not found: {}", path);
            return;
        }
        target.getStylesheets().setAll(url.toExternalForm());
        log.debug("Applied theme: {}", getTheme().label());
    }
}
