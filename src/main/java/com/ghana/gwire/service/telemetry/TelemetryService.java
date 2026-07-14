package com.ghana.gwire.service.telemetry;

import com.ghana.gwire.service.persist.GwireHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in, privacy-preserving event counters for product analytics.
 *
 * <p><b>Default OFF.</b> Only generic event names are recorded (e.g.
 * {@code app.start}, {@code calc.run}, {@code export.pdf}). Never log floor
 * plans, project names, file paths with user content, API keys, or other PII.
 *
 * <p>When enabled, appends one JSON line per event to
 * {@code ~/.gwire/telemetry.log} (or an injected path for tests).
 */
public final class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    /** Well-known generic event names (non-exhaustive). */
    public static final String EVENT_APP_START = "app.start";
    public static final String EVENT_CALC_RUN = "calc.run";
    public static final String EVENT_EXPORT_PDF = "export.pdf";

    private static final TelemetryService SHARED = new TelemetryService();

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final Path logPath;

    /** Shared instance writing to the default telemetry log under {@link GwireHome}. */
    public static TelemetryService get() {
        return SHARED;
    }

    public TelemetryService() {
        this(defaultLogPath());
    }

    /**
     * @param logPath destination file for append-only telemetry lines (tests inject a temp path)
     */
    public TelemetryService(Path logPath) {
        this.logPath = Objects.requireNonNull(logPath, "logPath");
    }

    public Path logPath() {
        return logPath;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean on) {
        enabled.set(on);
    }

    /** Records a generic event with no tags. No-op when disabled. */
    public void record(String eventName) {
        record(eventName, Map.of());
    }

    /**
     * Records a generic event with optional non-PII tags.
     * No-op when disabled or when {@code eventName} is blank.
     *
     * @param eventName short dotted name (e.g. {@code calc.run})
     * @param tags      non-PII string map (null treated as empty); values are escaped for JSON
     */
    public void record(String eventName, Map<String, String> tags) {
        if (!enabled.get()) {
            return;
        }
        if (eventName == null || eventName.isBlank()) {
            return;
        }
        String name = eventName.trim();
        Map<String, String> safeTags = tags == null || tags.isEmpty()
                ? Map.of()
                : new LinkedHashMap<>(tags);

        String line = toJsonLine(name, safeTags);
        try {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    logPath,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.debug("Telemetry write failed (ignored): {}", e.getMessage());
        }
    }

    private static String toJsonLine(String eventName, Map<String, String> tags) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"ts\":\"").append(Instant.now()).append('"');
        sb.append(",\"event\":\"").append(escapeJson(eventName)).append('"');
        if (!tags.isEmpty()) {
            sb.append(",\"tags\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : tags.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                String v = e.getValue() == null ? "" : e.getValue();
                sb.append('"').append(escapeJson(e.getKey().trim())).append('"')
                        .append(':')
                        .append('"').append(escapeJson(v)).append('"');
            }
            sb.append('}');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static Path defaultLogPath() {
        try {
            return GwireHome.telemetryLog();
        } catch (Throwable t) {
            return Path.of(System.getProperty("user.home"), ".gwire", "telemetry.log");
        }
    }
}
