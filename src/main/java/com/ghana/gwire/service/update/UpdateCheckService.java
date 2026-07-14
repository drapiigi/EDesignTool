package com.ghana.gwire.service.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghana.gwire.GWireApp;
import com.ghana.gwire.service.prefs.UserPrefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Non-blocking update check against a JSON version manifest.
 *
 * <p>Expected shape (see {@code docs/release/version.json}):
 * <pre>
 * { "latest": "0.9.1", "notes": "…", "url": "https://github.com/…/releases" }
 * </pre>
 */
public final class UpdateCheckService {

    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserPrefs prefs;
    private final HttpClient client;

    public UpdateCheckService(UserPrefs prefs) {
        this.prefs = prefs;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public record UpdateInfo(String latestVersion, String notes, String releaseUrl) {
    }

    /**
     * Async check; never throws to callers. Empty if disabled, offline, or up-to-date.
     */
    public CompletableFuture<Optional<UpdateInfo>> checkAsync() {
        if (!prefs.isUpdateCheckEnabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String url = prefs.versionCheckUrl();
        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> fetch(url));
    }

    Optional<UpdateInfo> fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", GWireApp.APP_NAME + "/" + GWireApp.APP_VERSION)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.debug("Update check HTTP {}", resp.statusCode());
                return Optional.empty();
            }
            JsonNode root = MAPPER.readTree(resp.body());
            String latest = text(root, "latest");
            if (latest == null || latest.isBlank()) {
                latest = text(root, "version");
            }
            if (latest == null || latest.isBlank()) {
                return Optional.empty();
            }
            String current = stripSnapshot(GWireApp.APP_VERSION);
            if (!isNewer(latest.trim(), current)) {
                return Optional.empty();
            }
            String notes = text(root, "notes");
            String releaseUrl = text(root, "url");
            if (releaseUrl == null) {
                releaseUrl = "https://github.com/drapiigi/EDesignTool/releases";
            }
            return Optional.of(new UpdateInfo(latest.trim(), notes == null ? "" : notes, releaseUrl));
        } catch (Exception e) {
            log.debug("Update check skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** True if candidate is strictly newer than current (simple dotted semver, ignores -suffix). */
    static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) {
                return true;
            }
            if (a[i] < b[i]) {
                return false;
            }
        }
        return false;
    }

    static String stripSnapshot(String v) {
        if (v == null) {
            return "0.0.0";
        }
        int dash = v.indexOf('-');
        return dash > 0 ? v.substring(0, dash) : v;
    }

    private static int[] parse(String v) {
        String s = stripSnapshot(v).replaceAll("[^0-9.]", "");
        String[] parts = s.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        return n.get(field).asText(null);
    }
}
