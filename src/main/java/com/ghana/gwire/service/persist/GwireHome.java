package com.ghana.gwire.service.persist;

import java.nio.file.Path;

/**
 * Standard locations under {@code ~/.gwire/}.
 */
public final class GwireHome {

    private GwireHome() {
    }

    public static Path root() {
        return Path.of(System.getProperty("user.home"), ".gwire");
    }

    public static Path autosaveDir() {
        return root().resolve("autosave");
    }

    public static Path logsDir() {
        return root().resolve("logs");
    }

    public static Path cacheMediaDir() {
        return root().resolve("cache").resolve("media");
    }

    public static Path secretsFile() {
        return root().resolve("secrets.properties");
    }

    public static Path cleanExitMarker() {
        return root().resolve("clean-exit");
    }

    public static Path aiProperties() {
        return root().resolve("ai.properties");
    }

    /** Opt-in generic product telemetry (no project content). */
    public static Path telemetryLog() {
        return root().resolve("telemetry.log");
    }
}
