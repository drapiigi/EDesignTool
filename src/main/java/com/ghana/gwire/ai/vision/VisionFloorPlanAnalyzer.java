package com.ghana.gwire.ai.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghana.gwire.ai.AiSettings;
import com.ghana.gwire.ai.OpenAiCompatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Multimodal vision analysis of a floor-plan raster (image/PDF-rendered page).
 * Requires a vision-capable OpenAI-compatible model and API key.
 *
 * <p>Returns empty Optional on failure; does not invent geometry without a model response.
 */
public final class VisionFloorPlanAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(VisionFloorPlanAnalyzer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiSettings settings;
    private final OpenAiCompatClient client;

    public VisionFloorPlanAnalyzer(AiSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.client = new OpenAiCompatClient(settings);
    }

    VisionFloorPlanAnalyzer(AiSettings settings, OpenAiCompatClient client) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.client = Objects.requireNonNull(client, "client");
    }

    public Optional<VisionFloorPlanResult> analyzeFile(Path imagePath) {
        if (!settings.isLlmAvailable()) {
            log.warn("Vision analysis skipped: LLM/API key not configured");
            return Optional.empty();
        }
        try {
            ImageEncoder.EncodedImage enc = ImageEncoder.encodeFile(imagePath);
            return analyzeEncoded(enc);
        } catch (Exception e) {
            log.warn("Vision analysis failed for {}: {}", imagePath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<VisionFloorPlanResult> analyzeEncoded(ImageEncoder.EncodedImage enc) {
        if (!settings.isLlmAvailable()) {
            return Optional.empty();
        }
        Objects.requireNonNull(enc, "enc");
        try {
            String system = systemPrompt();
            String user = userPrompt(enc.width(), enc.height());
            String raw = client.chatWithImage(system, user, enc.mediaType(), enc.base64());
            VisionFloorPlanResult result = parse(raw, enc.originalWidth(), enc.originalHeight());
            if (result == null || result.isEmpty()) {
                log.warn("Vision model returned no rooms/walls");
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (IOException e) {
            log.warn("Vision chat failed: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Vision parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Offline fallback: single full-plan room covering the image extents (no LLM).
     * Useful when no API key is set so the user can still proceed with rules-based design.
     */
    public static VisionFloorPlanResult offlineFullPlanRoom(int pixelW, int pixelH, String label) {
        String name = label == null || label.isBlank() ? "Imported plan" : label;
        VisionRoomHint room = new VisionRoomHint(name, 0.05, 0.05, 0.9, 0.9, "unknown");
        return new VisionFloorPlanResult(
                List.of(room),
                List.of(),
                List.of(),
                "Offline fallback: one room covering ~90% of the imported plan image. "
                        + "Configure GWIRE_AI_API_KEY + a vision model for real room detection.",
                0.2,
                "offline-fallback",
                Math.max(1, pixelW),
                Math.max(1, pixelH)
        );
    }

    static VisionFloorPlanResult parse(String raw, int pixelW, int pixelH) throws Exception {
        String json = stripFences(raw);
        JsonNode root = MAPPER.readTree(json);
        List<VisionRoomHint> rooms = new ArrayList<>();
        JsonNode roomsNode = root.path("rooms");
        if (roomsNode.isArray()) {
            for (JsonNode n : roomsNode) {
                rooms.add(new VisionRoomHint(
                        text(n, "name", "Room"),
                        num(n, "xNorm", "x", 0),
                        num(n, "yNorm", "y", 0),
                        num(n, "widthNorm", "w", 0.2),
                        num(n, "heightNorm", "h", 0.2),
                        text(n, "type", text(n, "roomType", ""))
                ));
            }
        }
        List<VisionWallHint> walls = new ArrayList<>();
        JsonNode wallsNode = root.path("walls");
        if (wallsNode.isArray()) {
            for (JsonNode n : wallsNode) {
                walls.add(new VisionWallHint(
                        num(n, "x1Norm", "x1", 0),
                        num(n, "y1Norm", "y1", 0),
                        num(n, "x2Norm", "x2", 0),
                        num(n, "y2Norm", "y2", 0)
                ));
            }
        }
        List<VisionOpeningHint> openings = new ArrayList<>();
        JsonNode openNode = root.path("openings");
        if (openNode.isArray()) {
            for (JsonNode n : openNode) {
                openings.add(new VisionOpeningHint(
                        text(n, "type", "DOOR"),
                        num(n, "xNorm", "x", 0.5),
                        num(n, "yNorm", "y", 0.5),
                        num(n, "widthNorm", "width", 0.05)
                ));
            }
        }
        double confidence = root.path("confidence").isNumber() ? root.path("confidence").asDouble(0.5) : 0.5;
        String notes = root.path("notes").asText("");
        // If model returned pixel coords mistakenly (values > 1), normalize
        rooms = normalizeIfNeeded(rooms);
        walls = normalizeWallsIfNeeded(walls);
        openings = normalizeOpeningsIfNeeded(openings);

        return new VisionFloorPlanResult(
                rooms, walls, openings, notes, confidence,
                "vision-llm",
                pixelW, pixelH
        );
    }

    private static List<VisionRoomHint> normalizeIfNeeded(List<VisionRoomHint> rooms) {
        boolean looksLikePixels = rooms.stream().anyMatch(r ->
                r.xNorm() > 1.5 || r.yNorm() > 1.5 || r.widthNorm() > 1.5 || r.heightNorm() > 1.5);
        if (!looksLikePixels) {
            return rooms;
        }
        double maxX = 1;
        double maxY = 1;
        for (VisionRoomHint r : rooms) {
            maxX = Math.max(maxX, r.xNorm() + r.widthNorm());
            maxY = Math.max(maxY, r.yNorm() + r.heightNorm());
        }
        List<VisionRoomHint> out = new ArrayList<>();
        for (VisionRoomHint r : rooms) {
            out.add(new VisionRoomHint(
                    r.name(),
                    r.xNorm() / maxX,
                    r.yNorm() / maxY,
                    r.widthNorm() / maxX,
                    r.heightNorm() / maxY,
                    r.roomType()
            ));
        }
        return out;
    }

    private static List<VisionWallHint> normalizeWallsIfNeeded(List<VisionWallHint> walls) {
        boolean looksLikePixels = walls.stream().anyMatch(w ->
                w.x1Norm() > 1.5 || w.y1Norm() > 1.5 || w.x2Norm() > 1.5 || w.y2Norm() > 1.5);
        if (!looksLikePixels) {
            return walls;
        }
        double max = 1;
        for (VisionWallHint w : walls) {
            max = Math.max(max, Math.max(Math.max(w.x1Norm(), w.x2Norm()), Math.max(w.y1Norm(), w.y2Norm())));
        }
        List<VisionWallHint> out = new ArrayList<>();
        for (VisionWallHint w : walls) {
            out.add(new VisionWallHint(w.x1Norm() / max, w.y1Norm() / max, w.x2Norm() / max, w.y2Norm() / max));
        }
        return out;
    }

    private static List<VisionOpeningHint> normalizeOpeningsIfNeeded(List<VisionOpeningHint> openings) {
        boolean looksLikePixels = openings.stream().anyMatch(o -> o.xNorm() > 1.5 || o.yNorm() > 1.5);
        if (!looksLikePixels) {
            return openings;
        }
        double max = 1;
        for (VisionOpeningHint o : openings) {
            max = Math.max(max, Math.max(o.xNorm(), o.yNorm()));
        }
        List<VisionOpeningHint> out = new ArrayList<>();
        for (VisionOpeningHint o : openings) {
            out.add(new VisionOpeningHint(o.type(), o.xNorm() / max, o.yNorm() / max, o.widthNorm()));
        }
        return out;
    }

    private static String systemPrompt() {
        return """
                You are a computer-vision assistant for architectural floor plans used in Ghana
                domestic electrical design (L.I. 2008 context). Analyse the image and extract
                approximate room rectangles and wall lines.

                Reply with JSON ONLY (no markdown fences). Schema:
                {
                  "confidence": 0.0-1.0,
                  "notes": "short string",
                  "rooms": [
                    {"name":"Living","type":"living|kitchen|bedroom|bathroom|other",
                     "xNorm":0.0,"yNorm":0.0,"widthNorm":0.3,"heightNorm":0.4}
                  ],
                  "walls": [
                    {"x1Norm":0.0,"y1Norm":0.0,"x2Norm":0.5,"y2Norm":0.0}
                  ],
                  "openings": [
                    {"type":"DOOR|WINDOW","xNorm":0.2,"yNorm":0.0,"widthNorm":0.05}
                  ]
                }

                Coordinates are NORMALIZED fractions of the image: 0 = left/top, 1 = right/bottom.
                Prefer 3–15 rooms for a typical house plan. Infer names from labels if visible.
                If uncertain, still provide best-effort boxes with lower confidence.
                """;
    }

    private static String userPrompt(int w, int h) {
        return String.format(
                Locale.ROOT,
                "Floor plan image is %dx%d pixels (you receive a scaled copy). "
                        + "Extract rooms/walls/openings as normalized 0–1 coordinates relative to the image. "
                        + "JSON only.",
                w, h
        );
    }

    private static String stripFences(String raw) {
        if (raw == null) {
            return "{}";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (firstNl > 0 && last > firstNl) {
                s = s.substring(firstNl + 1, last).trim();
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s;
    }

    private static String text(JsonNode n, String a, String b, String def) {
        if (n.hasNonNull(a)) {
            return n.get(a).asText(def);
        }
        if (b != null && n.hasNonNull(b)) {
            return n.get(b).asText(def);
        }
        return def;
    }

    private static String text(JsonNode n, String a, String def) {
        return text(n, a, null, def);
    }

    private static double num(JsonNode n, String a, String b, double def) {
        if (n.has(a) && n.get(a).isNumber()) {
            return n.get(a).asDouble(def);
        }
        if (b != null && n.has(b) && n.get(b).isNumber()) {
            return n.get(b).asDouble(def);
        }
        if (n.has(a)) {
            try {
                return Double.parseDouble(n.get(a).asText());
            } catch (Exception ignored) {
                // fall through
            }
        }
        return def;
    }
}
