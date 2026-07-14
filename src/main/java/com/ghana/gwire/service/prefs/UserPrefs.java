package com.ghana.gwire.service.prefs;

import com.ghana.gwire.service.persist.GwireHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lightweight user preferences under {@code ~/.gwire/prefs.properties}.
 */
public final class UserPrefs {

    private static final Logger log = LoggerFactory.getLogger(UserPrefs.class);

    public static final String KEY_FIRST_RUN_ACCEPTED = "firstRun.disclaimerAccepted";
    public static final String KEY_FIRST_RUN_VERSION = "firstRun.acceptedAppVersion";
    public static final String KEY_UPDATE_CHECK_ENABLED = "update.checkEnabled";
    public static final String KEY_UPDATE_URL = "update.versionUrl";
    public static final String KEY_BUILD_SIGNED = "build.signed";
    /** Opt-in anonymous product telemetry (default false). */
    public static final String KEY_TELEMETRY_OPT_IN = "telemetry.optIn";

    /** Default GitHub raw / release-friendly version manifest (override via prefs or env). */
    public static final String DEFAULT_VERSION_URL =
            "https://raw.githubusercontent.com/drapiigi/EDesignTool/main/docs/release/version.json";

    private final Path path;
    private final Properties props = new Properties();

    public UserPrefs() {
        this(GwireHome.root().resolve("prefs.properties"));
    }

    public UserPrefs(Path path) {
        this.path = path;
        load();
    }

    public Path path() {
        return path;
    }

    public boolean isFirstRunAccepted() {
        return "true".equalsIgnoreCase(props.getProperty(KEY_FIRST_RUN_ACCEPTED, "false"));
    }

    public void setFirstRunAccepted(String appVersion) {
        props.setProperty(KEY_FIRST_RUN_ACCEPTED, "true");
        if (appVersion != null) {
            props.setProperty(KEY_FIRST_RUN_VERSION, appVersion);
        }
        save();
    }

    public boolean isUpdateCheckEnabled() {
        return !"false".equalsIgnoreCase(props.getProperty(KEY_UPDATE_CHECK_ENABLED, "true"));
    }

    public String versionCheckUrl() {
        String env = System.getenv("GWIRE_UPDATE_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return props.getProperty(KEY_UPDATE_URL, DEFAULT_VERSION_URL);
    }

    /**
     * Whether this build claims to be signed. Default false (unsigned beta).
     * Set via system property {@code gwire.build.signed=true} or prefs.
     */
    public boolean isBuildSigned() {
        String sys = System.getProperty("gwire.build.signed");
        if (sys != null) {
            return "true".equalsIgnoreCase(sys.trim());
        }
        return "true".equalsIgnoreCase(props.getProperty(KEY_BUILD_SIGNED, "false"));
    }

    /**
     * Whether the user opted in to generic product telemetry. Default {@code false}.
     */
    public boolean isTelemetryOptIn() {
        return "true".equalsIgnoreCase(props.getProperty(KEY_TELEMETRY_OPT_IN, "false"));
    }

    public void setTelemetryOptIn(boolean optIn) {
        props.setProperty(KEY_TELEMETRY_OPT_IN, optIn ? "true" : "false");
        save();
    }

    private void load() {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            log.warn("Could not read prefs {}: {}", path, e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "GhanaWire AI user preferences");
            }
        } catch (IOException e) {
            log.warn("Could not save prefs {}: {}", path, e.getMessage());
        }
    }
}
