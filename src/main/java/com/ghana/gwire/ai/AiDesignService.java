package com.ghana.gwire.ai;

import com.ghana.gwire.ai.vision.ImageEncoder;
import com.ghana.gwire.ai.vision.VisionFloorPlanAnalyzer;
import com.ghana.gwire.ai.vision.VisionFloorPlanResult;
import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.ComponentSeed;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Facade for AI-assisted design: LLM when configured, rule-based fallback, apply to plan, co-pilot.
 *
 * <p>UI integration:
 * <pre>
 * AiDesignService ai = new AiDesignService();
 * AiDesignPlan plan = ai.generate(project, LibraryBootstrap.get());
 * int n = ai.apply(project, plan, true);
 * String reply = ai.coPilot(project, LibraryBootstrap.get(), userText);
 * </pre>
 */
public final class AiDesignService {

    private static final Logger log = LoggerFactory.getLogger(AiDesignService.class);

    private final AiSettings settings;
    private final RuleBasedDesignGenerator rules;
    private final LlmDesignGenerator llm;
    private final VisionFloorPlanAnalyzer vision;

    public AiDesignService() {
        this(AiSettings.load());
    }

    public AiDesignService(AiSettings settings) {
        this.settings = settings == null ? AiSettings.load() : settings;
        this.rules = new RuleBasedDesignGenerator();
        this.llm = new LlmDesignGenerator(this.settings);
        this.vision = new VisionFloorPlanAnalyzer(this.settings);
    }

    /** Test hook with injectable LLM generator. */
    AiDesignService(AiSettings settings, RuleBasedDesignGenerator rules, LlmDesignGenerator llm) {
        this.settings = settings == null ? AiSettings.load() : settings;
        this.rules = rules == null ? new RuleBasedDesignGenerator() : rules;
        this.llm = llm;
        this.vision = new VisionFloorPlanAnalyzer(this.settings);
    }

    public AiSettings settings() {
        return settings;
    }

    /**
     * Generate a design plan: try LLM when available, fall back to rules on empty/failure.
     */
    public AiDesignPlan generate(Project project, ComponentLibraryService library) {
        Objects.requireNonNull(project, "project");
        List<ElectricalComponent> catalogue = resolveCatalogue(library);

        if (settings.isLlmAvailable() && llm != null) {
            Optional<AiDesignPlan> llmPlan = llm.generate(project, catalogue);
            if (llmPlan.isPresent() && !llmPlan.get().isEmpty()) {
                log.info("AI design via LLM: {} placements", llmPlan.get().size());
                return llmPlan.get();
            }
            log.warn("LLM design unavailable or empty — falling back to rule-based generator");
            AiDesignPlan rulePlan = rules.generate(project, catalogue);
            String notes = rulePlan.notes()
                    + "\n(Fallback after LLM; LLM key present=" + settings.isLlmAvailable() + ")";
            return new AiDesignPlan(
                    AiDesignPlan.Source.HYBRID,
                    notes,
                    rulePlan.placements(),
                    "rules-fallback after LLM · " + rulePlan.providerDetail()
            );
        }

        return generateRulesOnly(project, catalogue);
    }

    /** Force offline rule-based generation (ignores LLM settings). */
    public AiDesignPlan generateRulesOnly(Project project, ComponentLibraryService library) {
        return generateRulesOnly(project, resolveCatalogue(library));
    }

    public AiDesignPlan generateRulesOnly(Project project, List<ElectricalComponent> catalogue) {
        Objects.requireNonNull(project, "project");
        return rules.generate(project, catalogue == null ? List.of() : catalogue);
    }

    /**
     * Analyses an imported floor-plan image with a vision model (or offline fallback).
     * Does not modify the project — call {@link VisionFloorPlanResult#applyTo} or
     * {@link #analyzeAndApplyVision}.
     */
    public Optional<VisionFloorPlanResult> analyzeFloorPlanImage(Path imagePath) {
        Objects.requireNonNull(imagePath, "imagePath");
        Optional<VisionFloorPlanResult> llm = vision.analyzeFile(imagePath);
        if (llm.isPresent()) {
            return llm;
        }
        try {
            ImageEncoder.EncodedImage enc = ImageEncoder.encodeFile(imagePath);
            log.info("Using offline full-plan room fallback for vision (no LLM result)");
            return Optional.of(VisionFloorPlanAnalyzer.offlineFullPlanRoom(
                    enc.originalWidth(), enc.originalHeight(), imagePath.getFileName().toString()
            ));
        } catch (Exception e) {
            log.warn("Could not encode image for offline vision fallback: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vision-analyse the project's background floor plan and apply rooms/walls.
     *
     * @return result summary; empty if no background or analysis failed
     */
    public Optional<VisionFloorPlanResult> analyzeAndApplyVision(
            Project project,
            boolean clearExistingRoomsWalls,
            boolean clearDevices
    ) {
        Objects.requireNonNull(project, "project");
        BackgroundImage bg = project.floorPlan().background();
        if (bg == null || bg.sourcePath() == null || bg.sourcePath().isBlank()) {
            log.warn("Vision apply skipped: no background image on project");
            return Optional.empty();
        }
        Path path = Path.of(bg.sourcePath());
        Optional<VisionFloorPlanResult> result = analyzeFloorPlanImage(path);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        VisionFloorPlanResult r = result.get();
        int rooms = r.applyTo(project.floorPlan(), bg, clearExistingRoomsWalls, clearDevices);
        project.touch();
        log.info("Vision applied: {} rooms · {}", rooms, r.summary());
        return Optional.of(r);
    }

    /**
     * Full pipeline: vision rooms from background → electrical design (LLM or rules) → devices.
     *
     * @return number of devices placed; −1 if vision/geometry failed
     */
    public int generateFromVisionBackground(
            Project project,
            ComponentLibraryService library,
            boolean clearRoomsWalls,
            boolean clearDevices
    ) {
        Optional<VisionFloorPlanResult> visionResult =
                analyzeAndApplyVision(project, clearRoomsWalls, clearDevices);
        if (visionResult.isEmpty()) {
            return -1;
        }
        if (project.floorPlan().rooms().isEmpty()) {
            log.warn("Vision produced no rooms — skipping electrical generate");
            return 0;
        }
        AiDesignPlan plan = generate(project, library);
        return apply(project, plan, true);
    }

    /**
     * Applies placements to the project floor plan as {@link PlacedDevice}s.
     *
     * @param clearExistingDevices if true, remove existing devices (rooms/walls kept)
     * @return number of devices added
     */
    public int apply(Project project, AiDesignPlan plan, boolean clearExistingDevices) {
        Objects.requireNonNull(project, "project");
        if (plan == null || plan.isEmpty()) {
            return 0;
        }
        FloorPlan fp = project.floorPlan();
        if (clearExistingDevices) {
            fp.clearDevices();
        }
        int count = 0;
        for (DesignPlacement p : plan.placements()) {
            PlacedDevice d = new PlacedDevice(
                    p.componentId(),
                    p.symbolKey(),
                    p.xMm(),
                    p.yMm()
            );
            d.setNameOverride(p.name());
            d.setRotationDeg(p.rotationDeg());
            d.setRoomId(p.roomId());
            fp.addDevice(d);
            count++;
        }
        project.touch();
        log.info("Applied {} AI placements (source={})", count, plan.source());
        return count;
    }

    /**
     * Simple co-pilot: rule-based natural-language-ish commands; optional LLM one-shot if free text.
     */
    public String coPilot(Project project, ComponentLibraryService library, String userMessage) {
        return coPilotResult(project, library, userMessage).reply();
    }

    /**
     * Co-pilot with optional plan the UI may apply.
     */
    public AiCopilotResult coPilotResult(Project project, ComponentLibraryService library, String userMessage) {
        Objects.requireNonNull(project, "project");
        String msg = userMessage == null ? "" : userMessage.trim();
        if (msg.isEmpty()) {
            return AiCopilotResult.text(helpText());
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        List<ElectricalComponent> catalogue = resolveCatalogue(library);

        if (containsAny(lower, "generate", "design", "auto-place", "auto place")) {
            return AiCopilotResult.text(
                    "Use Design → AI Generate Design to place devices from rules or LLM. "
                            + "Then Tools → Recalculate Loads to check L.I. 2008 practice."
            );
        }
        if (containsAny(lower, "recalculate", "recalc", "calc", "validate", "loads")) {
            return AiCopilotResult.text(
                    "Open Tools → Recalculate Loads or Tools → Validate Standards (L.I. 2008)."
            );
        }

        Room room = findRoomMention(project, lower);
        if (containsAny(lower, "socket", "outlet", "plug") && room != null) {
            AiDesignPlan plan = socketsForRoom(room, catalogue, 2);
            int n = apply(project, plan, false);
            return AiCopilotResult.withPlan(
                    "Added " + n + " twin socket(s) to room '" + room.name() + "'.",
                    plan
            );
        }
        if (containsAny(lower, "light", "lamp", "luminaire") && room != null) {
            AiDesignPlan plan = lightForRoom(room, catalogue);
            int n = apply(project, plan, false);
            return AiCopilotResult.withPlan(
                    "Added " + n + " light(s) to room '" + room.name() + "'.",
                    plan
            );
        }
        if (containsAny(lower, "socket", "outlet", "plug", "light", "lamp") && room == null) {
            return AiCopilotResult.text(
                    "Name a room, e.g. \"add socket in Living\" or \"add light to Kitchen\". "
                            + "Rooms on plan: " + roomNames(project)
            );
        }

        // Optional LLM free-text assist (no placements applied)
        if (settings.isLlmAvailable() && llm != null && msg.length() > 12) {
            try {
                OpenAiCompatClient client = new OpenAiCompatClient(settings);
                String reply = client.chat(
                        "You are GhanaWire AI co-pilot for domestic electrical design in Ghana (L.I. 2008). "
                                + "Answer briefly (under 120 words). Do not invent regulations. "
                                + "Suggest UI actions: AI Generate Design, Recalculate Loads, place sockets/lights.",
                        "Project rooms: " + roomNames(project) + "\nUser: " + msg
                );
                if (reply != null && !reply.isBlank()) {
                    return AiCopilotResult.text(reply.trim());
                }
            } catch (Exception e) {
                log.warn("Co-pilot LLM assist failed: {}", e.getMessage());
            }
        }

        return AiCopilotResult.text(helpText());
    }

    private AiDesignPlan socketsForRoom(Room room, List<ElectricalComponent> catalogue, int count) {
        ElectricalComponent sock = RuleBasedDesignGenerator.resolve(
                index(catalogue), catalogue, "SOCK-13A-2G",
                com.ghana.gwire.domain.components.ComponentCategory.SOCKET, "13a"
        );
        List<DesignPlacement> list = new ArrayList<>();
        if (sock == null) {
            return new AiDesignPlan(AiDesignPlan.Source.RULES, "No socket in catalogue", list, "copilot");
        }
        for (int i = 0; i < count; i++) {
            double x = room.x() + 400 + i * 600;
            double y = room.y() + room.heightMm() - 400;
            x = Math.min(x, room.x() + room.widthMm() - 100);
            y = Math.max(y, room.y() + 100);
            list.add(new DesignPlacement(
                    sock.id(), sock.symbolKey(), sock.name() + " · " + room.name(),
                    x, y, room.id(), 0
            ));
        }
        return new AiDesignPlan(AiDesignPlan.Source.RULES, "Co-pilot: add sockets", list, "copilot-rules");
    }

    private AiDesignPlan lightForRoom(Room room, List<ElectricalComponent> catalogue) {
        ElectricalComponent light = RuleBasedDesignGenerator.resolve(
                index(catalogue), catalogue, "LIGHT-LED-9W",
                com.ghana.gwire.domain.components.ComponentCategory.LIGHTING, "led"
        );
        List<DesignPlacement> list = new ArrayList<>();
        if (light == null) {
            return new AiDesignPlan(AiDesignPlan.Source.RULES, "No light in catalogue", list, "copilot");
        }
        list.add(new DesignPlacement(
                light.id(), light.symbolKey(), light.name() + " · " + room.name(),
                room.x() + room.widthMm() / 2.0,
                room.y() + room.heightMm() / 2.0,
                room.id(),
                0
        ));
        return new AiDesignPlan(AiDesignPlan.Source.RULES, "Co-pilot: add light", list, "copilot-rules");
    }

    private static java.util.Map<String, ElectricalComponent> index(List<ElectricalComponent> cat) {
        java.util.Map<String, ElectricalComponent> map = new java.util.HashMap<>();
        for (ElectricalComponent c : cat) {
            map.put(c.id(), c);
        }
        return map;
    }

    private static Room findRoomMention(Project project, String lowerMsg) {
        Room best = null;
        int bestLen = 0;
        for (Room r : project.floorPlan().rooms()) {
            String rn = r.name().toLowerCase(Locale.ROOT);
            if (rn.length() >= 2 && lowerMsg.contains(rn) && rn.length() > bestLen) {
                best = r;
                bestLen = rn.length();
            }
        }
        return best;
    }

    private static String roomNames(Project project) {
        List<String> names = project.floorPlan().rooms().stream().map(Room::name).toList();
        return names.isEmpty() ? "(none — draw rooms first)" : String.join(", ", names);
    }

    private static boolean containsAny(String lower, String... tokens) {
        for (String t : tokens) {
            if (lower.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static String helpText() {
        return """
                GhanaWire AI co-pilot commands (examples):
                • "add socket in Living" — place twin sockets in that room
                • "add light to Kitchen" — place a light at room centre
                • "generate design" — use Design → AI Generate Design
                • "recalculate" — use Tools → Recalculate Loads
                Configure LLM via ~/.gwire/ai.properties or GWIRE_AI_API_KEY (never commit keys).
                """;
    }

    private static List<ElectricalComponent> resolveCatalogue(ComponentLibraryService library) {
        if (library != null) {
            try {
                List<ElectricalComponent> all = library.listAll();
                if (all != null && !all.isEmpty()) {
                    return all;
                }
            } catch (Exception e) {
                log.warn("Catalogue from library failed, using seed: {}", e.getMessage());
            }
        }
        return ComponentSeed.starterCatalogue();
    }
}
