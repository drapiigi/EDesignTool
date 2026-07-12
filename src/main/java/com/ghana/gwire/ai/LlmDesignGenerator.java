package com.ghana.gwire.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM-backed design placement generator (OpenAI-compatible chat API).
 * Returns empty Optional on any failure; callers should fall back to rules.
 */
public final class LlmDesignGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmDesignGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CATALOGUE_IDS = 40;

    private final OpenAiCompatClient client;
    private final AiSettings settings;

    public LlmDesignGenerator(AiSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.client = new OpenAiCompatClient(settings);
    }

    LlmDesignGenerator(AiSettings settings, OpenAiCompatClient client) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.client = Objects.requireNonNull(client, "client");
    }

    public Optional<AiDesignPlan> generate(Project project, List<ElectricalComponent> catalogue) {
        if (!settings.isLlmAvailable()) {
            return Optional.empty();
        }
        List<ElectricalComponent> cat = catalogue == null ? List.of() : catalogue;
        if (cat.isEmpty()) {
            log.warn("LLM design skipped: empty catalogue");
            return Optional.empty();
        }
        try {
            String system = systemPrompt();
            String user = userPrompt(project, cat);
            String raw = client.chat(system, user);
            AiDesignPlan plan = parsePlan(raw, project, cat);
            if (plan == null || plan.isEmpty()) {
                log.warn("LLM design produced empty placements");
                return Optional.empty();
            }
            return Optional.of(plan);
        } catch (Exception e) {
            // Never log key material
            log.warn("LLM design generation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String systemPrompt() {
        return """
                You are an electrical design assistant for Ghana domestic wiring
                (L.I. 2008 / Energy Commission practice). Propose device placements
                on a floor plan in millimetres. Reply with JSON ONLY — no markdown,
                no commentary. Schema:
                {"placements":[{"componentId":"SOCK-13A-2G","xMm":1000,"yMm":2000,"roomId":"...","name":"...","rotationDeg":0}],"notes":"..."}
                Use only componentId values from the provided catalogue list.
                Place lights near room centres, sockets along walls, switches near room entrances,
                and include a consumer unit + RCCB + earth electrode for the dwelling when appropriate.
                """;
    }

    private static String userPrompt(Project project, List<ElectricalComponent> cat) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(project.name()).append('\n');
        sb.append("Supply: ").append(project.supplySummary()).append('\n');
        sb.append("House type: ").append(project.settings().houseType()).append('\n');
        sb.append("Rooms (id, name, x,y,width,height mm, area m2):\n");
        for (Room r : project.floorPlan().rooms()) {
            sb.append(String.format(
                    Locale.ROOT,
                    "  - id=%s name=%s x=%.0f y=%.0f w=%.0f h=%.0f area=%.2f\n",
                    r.id(), r.name(), r.x(), r.y(), r.widthMm(), r.heightMm(), r.areaM2()
            ));
        }
        if (project.floorPlan().rooms().isEmpty()) {
            sb.append("  (none — place global protection/earthing only)\n");
        }
        sb.append("Catalogue componentIds (use only these, top ")
                .append(MAX_CATALOGUE_IDS).append("):\n");
        List<String> ids = preferredCatalogueIds(cat);
        sb.append("  ").append(String.join(", ", ids)).append('\n');
        sb.append("Return JSON only.");
        return sb.toString();
    }

    /**
     * Prefer placeable categories first, then fill remaining slots.
     */
    static List<String> preferredCatalogueIds(List<ElectricalComponent> cat) {
        List<String> preferred = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        var order = List.of(
                com.ghana.gwire.domain.components.ComponentCategory.LIGHTING,
                com.ghana.gwire.domain.components.ComponentCategory.SOCKET,
                com.ghana.gwire.domain.components.ComponentCategory.SWITCH,
                com.ghana.gwire.domain.components.ComponentCategory.DISTRIBUTION_BOARD,
                com.ghana.gwire.domain.components.ComponentCategory.PROTECTION,
                com.ghana.gwire.domain.components.ComponentCategory.EARTHING,
                com.ghana.gwire.domain.components.ComponentCategory.CIRCUIT_BREAKER
        );
        outer:
        for (var catOrder : order) {
            for (ElectricalComponent c : cat) {
                if (c.category() == catOrder && seen.add(c.id())) {
                    preferred.add(c.id());
                    if (preferred.size() >= MAX_CATALOGUE_IDS) {
                        break outer;
                    }
                }
            }
        }
        for (ElectricalComponent c : cat) {
            if (preferred.size() >= MAX_CATALOGUE_IDS) {
                break;
            }
            if (seen.add(c.id())) {
                preferred.add(c.id());
            }
        }
        return preferred;
    }

    AiDesignPlan parsePlan(String raw, Project project, List<ElectricalComponent> cat) throws Exception {
        String json = stripMarkdownFences(raw);
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        JsonNode arr = root.get("placements");
        if (arr == null || !arr.isArray()) {
            throw new IllegalArgumentException("Missing placements array");
        }

        Map<String, ElectricalComponent> byId = cat.stream()
                .collect(Collectors.toMap(ElectricalComponent::id, c -> c, (a, b) -> a));
        Map<String, Room> roomsById = new HashMap<>();
        for (Room r : project.floorPlan().rooms()) {
            roomsById.put(r.id(), r);
        }
        // Also allow matching by room name
        Map<String, Room> roomsByName = new HashMap<>();
        for (Room r : project.floorPlan().rooms()) {
            roomsByName.put(r.name().toLowerCase(Locale.ROOT), r);
        }

        List<DesignPlacement> placements = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || !n.isObject()) {
                continue;
            }
            String componentId = text(n, "componentId");
            if (componentId == null || componentId.isBlank()) {
                continue;
            }
            ElectricalComponent comp = byId.get(componentId);
            if (comp == null) {
                log.debug("Skipping unknown componentId from LLM: {}", componentId);
                continue;
            }
            double x = n.path("xMm").asDouble(Double.NaN);
            double y = n.path("yMm").asDouble(Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                continue;
            }
            String roomId = text(n, "roomId");
            Room room = null;
            if (roomId != null && !roomId.isBlank()) {
                room = roomsById.get(roomId);
                if (room == null) {
                    room = roomsByName.get(roomId.toLowerCase(Locale.ROOT));
                    if (room != null) {
                        roomId = room.id();
                    } else {
                        roomId = null;
                    }
                }
            }
            if (room != null) {
                double[] clamped = clampIntoRoom(x, y, room);
                x = clamped[0];
                y = clamped[1];
            } else {
                // Soft clamp near plan extents if rooms exist
                if (!project.floorPlan().rooms().isEmpty()) {
                    double[] soft = softClampToPlan(x, y, project.floorPlan().rooms());
                    x = soft[0];
                    y = soft[1];
                }
            }
            String name = text(n, "name");
            if (name == null || name.isBlank()) {
                name = comp.name();
            }
            double rot = n.path("rotationDeg").asDouble(0);
            placements.add(new DesignPlacement(
                    comp.id(), comp.symbolKey(), name, x, y, roomId, rot
            ));
        }

        String notes = text(root, "notes");
        if (notes == null) {
            notes = "LLM-generated placement plan";
        }
        String detail = settings.provider() + " · " + settings.model() + " @ " + settings.baseUrl();
        return new AiDesignPlan(AiDesignPlan.Source.LLM, notes, placements, detail);
    }

    static String stripMarkdownFences(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.trim();
        }
        // Extract outermost JSON object if prose wraps it
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s;
    }

    private static double[] clampIntoRoom(double x, double y, Room room) {
        double margin = 100;
        double minX = room.x() + margin;
        double maxX = room.x() + room.widthMm() - margin;
        double minY = room.y() + margin;
        double maxY = room.y() + room.heightMm() - margin;
        if (minX > maxX) {
            minX = maxX = room.x() + room.widthMm() / 2.0;
        }
        if (minY > maxY) {
            minY = maxY = room.y() + room.heightMm() / 2.0;
        }
        return new double[]{
                Math.max(minX, Math.min(maxX, x)),
                Math.max(minY, Math.min(maxY, y))
        };
    }

    private static double[] softClampToPlan(double x, double y, List<Room> rooms) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Room r : rooms) {
            minX = Math.min(minX, r.x() - 2000);
            minY = Math.min(minY, r.y() - 2000);
            maxX = Math.max(maxX, r.x() + r.widthMm() + 2000);
            maxY = Math.max(maxY, r.y() + r.heightMm() + 2000);
        }
        return new double[]{
                Math.max(minX, Math.min(maxX, x)),
                Math.max(minY, Math.min(maxY, y))
        };
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText();
    }
}
