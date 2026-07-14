package com.ghana.gwire.service.persist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.electrical.ChecklistReview;
import com.ghana.gwire.domain.electrical.Circuit;
import com.ghana.gwire.domain.electrical.ConsumerUnit;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.LinearDimension;
import com.ghana.gwire.domain.floorplan.Opening;
import com.ghana.gwire.domain.floorplan.OpeningType;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.floorplan.WiringRoute;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.domain.project.ProjectSettings;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Load/save GhanaWire projects as JSON ({@code .gwire} files) or packages ({@code .gwirez}).
 *
 * <p>Format 1.4: linear dimensions (13b). Loads 1.0–1.4.
 * Calculation reports are not persisted (re-run Tools → Recalculate Loads).
 */
public final class ProjectStore {

    /**
     * Current write version. 1.0 single floor; 1.1 multi-storey; 1.2 package metadata;
     * 1.3 first-class circuits / consumer unit / checklist; 1.4 linear dimensions (13b).
     */
    public static final String FORMAT_VERSION = "1.4";
    public static final String FILE_EXTENSION = "gwire";
    public static final String PACKAGE_EXTENSION = "gwirez";

    private static final Logger log = LoggerFactory.getLogger(ProjectStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Save with backup rotation (user Save / Save As). */
    public void save(Project project, Path path) throws IOException {
        save(project, path, true);
    }

    /**
     * @param rotateBackup if true, rotates {@code .bak}/{@code .bak2} before replace
     *                     (skip for ephemeral autosave)
     */
    public void save(Project project, Path path, boolean rotateBackup) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (name.endsWith("." + PACKAGE_EXTENSION)) {
            ProjectPackage.save(project, path, this);
            return;
        }
        project.touch();
        byte[] json = toJsonBytes(project);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (rotateBackup) {
            BackupService.rotate(path);
        }
        AtomicFileWriter.writeAtomically(path, json);
        log.info("Saved project '{}' to {} ({} storeys)", project.name(), path, project.storeys().size());
    }

    /** Serialize project to pretty-printed JSON bytes (formatVersion {@link #FORMAT_VERSION}). */
    public byte[] toJsonBytes(Project project) throws IOException {
        ObjectNode root = buildRoot(project);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    ObjectNode buildRoot(Project project) {
        project.touch();
        ObjectNode root = MAPPER.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("app", "GhanaWire AI");
        root.put("id", project.id());
        root.put("name", project.name());
        root.put("createdAt", project.createdAt().toString());
        root.put("modifiedAt", project.modifiedAt().toString());
        root.set("settings", writeSettings(project.settings()));
        root.set("floorPlan", writeFloorPlan(project.floorPlan()));
        root.put("activeStoreyIndex", project.activeStoreyIndex());
        ArrayNode storeysNode = root.putArray("storeys");
        for (BuildingStorey s : project.storeys()) {
            ObjectNode sn = storeysNode.addObject();
            sn.put("id", s.id());
            sn.put("name", s.name());
            sn.put("level", s.level());
            sn.set("floorPlan", writeFloorPlan(s.floorPlan()));
        }
        root.set("circuits", writeCircuits(project.circuits()));
        if (project.consumerUnit() != null) {
            root.set("consumerUnit", writeConsumerUnit(project.consumerUnit()));
        }
        root.set("checklistReview", writeChecklist(project.checklistReview()));
        return root;
    }

    public Project load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Project file not found: " + path);
        }
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (name.endsWith("." + PACKAGE_EXTENSION)) {
            return ProjectPackage.load(path, this);
        }
        JsonNode root = MAPPER.readTree(path.toFile());
        return loadFromTree(root);
    }

    /** Load from already-parsed JSON (used by package loader). */
    public Project loadFromTree(JsonNode root) throws IOException {
        String version = text(root, "formatVersion", "1.0");
        if (!version.startsWith("1")) {
            throw new IOException("Unsupported project format version: " + version);
        }

        Instant created = parseInstant(text(root, "createdAt", null));
        Instant modified = parseInstant(text(root, "modifiedAt", null));
        Project project = new Project(
                text(root, "id", null),
                text(root, "name", "Untitled project"),
                created,
                modified
        );
        readSettings(root.path("settings"), project.settings());

        JsonNode storeysNode = root.path("storeys");
        if (storeysNode.isArray() && storeysNode.size() > 0) {
            List<BuildingStorey> loaded = new ArrayList<>();
            for (JsonNode sn : storeysNode) {
                FloorPlan fp = new FloorPlan();
                readFloorPlan(sn.path("floorPlan"), fp);
                loaded.add(new BuildingStorey(
                        text(sn, "id", null),
                        text(sn, "name", "Floor"),
                        sn.path("level").asInt(0),
                        fp
                ));
            }
            int active = root.path("activeStoreyIndex").asInt(0);
            project.replaceStoreys(loaded, active);
        } else {
            // Format 1.0: single floorPlan
            readFloorPlan(root.path("floorPlan"), project.floorPlan());
        }
        readCircuits(root.path("circuits"), project);
        readConsumerUnit(root.path("consumerUnit"), project);
        readChecklist(root.path("checklistReview"), project);
        // lastReport is not persisted; leave null
        log.info("Loaded project '{}' ({} storeys, {} rooms, {} devices, {} circuits)",
                project.name(),
                project.storeys().size(),
                project.totalRoomCount(),
                project.totalDeviceCount(),
                project.circuits().size());
        return project;
    }

    ObjectMapper mapper() {
        return MAPPER;
    }

    private ObjectNode writeSettings(ProjectSettings s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("houseType", s.houseType());
        n.put("supplyType", s.supplyType().name());
        n.put("nominalVoltageV", s.nominalVoltageV());
        n.put("frequencyHz", s.frequencyHz());
        n.put("standardsEdition", s.standardsEdition());
        return n;
    }

    private void readSettings(JsonNode n, ProjectSettings s) {
        if (n == null || n.isMissingNode()) {
            return;
        }
        if (n.hasNonNull("houseType")) {
            s.setHouseType(n.get("houseType").asText());
        }
        if (n.hasNonNull("supplyType")) {
            try {
                s.setSupplyType(ProjectSettings.SupplyType.valueOf(n.get("supplyType").asText()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown supplyType in project file, using default");
            }
        }
        if (n.hasNonNull("standardsEdition")) {
            s.setStandardsEdition(n.get("standardsEdition").asText());
        }
    }

    private ObjectNode writeFloorPlan(FloorPlan fp) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("gridMm", fp.gridMm());
        n.put("snapToGrid", fp.isSnapToGrid());

        ArrayNode walls = n.putArray("walls");
        for (Wall w : fp.walls()) {
            ObjectNode wn = walls.addObject();
            wn.put("id", w.id());
            wn.put("x1", w.start().x());
            wn.put("y1", w.start().y());
            wn.put("x2", w.end().x());
            wn.put("y2", w.end().y());
            wn.put("thicknessMm", w.thicknessMm());
        }

        ArrayNode rooms = n.putArray("rooms");
        for (Room r : fp.rooms()) {
            ObjectNode rn = rooms.addObject();
            rn.put("id", r.id());
            rn.put("name", r.name());
            rn.put("x", r.x());
            rn.put("y", r.y());
            rn.put("widthMm", r.widthMm());
            rn.put("heightMm", r.heightMm());
        }

        ArrayNode openings = n.putArray("openings");
        for (Opening o : fp.openings()) {
            ObjectNode on = openings.addObject();
            on.put("id", o.id());
            on.put("wallId", o.wallId());
            on.put("type", o.type().name());
            on.put("t", o.t());
            on.put("widthMm", o.widthMm());
        }

        ArrayNode devices = n.putArray("devices");
        for (PlacedDevice d : fp.devices()) {
            ObjectNode dn = devices.addObject();
            dn.put("id", d.id());
            dn.put("componentId", d.componentId());
            dn.put("symbolKey", d.symbolKey());
            if (d.nameOverride() != null) {
                dn.put("nameOverride", d.nameOverride());
            }
            dn.put("xMm", d.xMm());
            dn.put("yMm", d.yMm());
            dn.put("rotationDeg", d.rotationDeg());
            if (d.roomId() != null) {
                dn.put("roomId", d.roomId());
            }
            if (d.circuitId() != null) {
                dn.put("circuitId", d.circuitId());
            }
            if (d.mountingHeightMm() > 0) {
                dn.put("mountingHeightMm", d.mountingHeightMm());
            }
        }

        n.put("showWiringRoutes", fp.isShowWiringRoutes());
        ArrayNode routes = n.putArray("wiringRoutes");
        for (WiringRoute wr : fp.wiringRoutes()) {
            ObjectNode rn = routes.addObject();
            rn.put("id", wr.id());
            if (wr.circuitId() != null) {
                rn.put("circuitId", wr.circuitId());
            }
            if (wr.cableComponentId() != null) {
                rn.put("cableComponentId", wr.cableComponentId());
            }
            rn.put("label", wr.label());
            ArrayNode pts = rn.putArray("points");
            for (Vec2 p : wr.points()) {
                ObjectNode pn = pts.addObject();
                pn.put("x", p.x());
                pn.put("y", p.y());
            }
        }

        ArrayNode dims = n.putArray("dimensions");
        for (LinearDimension d : fp.dimensions()) {
            ObjectNode dn = dims.addObject();
            dn.put("id", d.id());
            dn.put("x1", d.p1().x());
            dn.put("y1", d.p1().y());
            dn.put("x2", d.p2().x());
            dn.put("y2", d.p2().y());
            dn.put("offsetMm", d.offsetMm());
            if (d.labelOverride() != null && !d.labelOverride().isBlank()) {
                dn.put("labelOverride", d.labelOverride());
            }
        }

        if (fp.background() != null) {
            BackgroundImage bg = fp.background();
            ObjectNode bn = n.putObject("background");
            bn.put("sourcePath", bg.sourcePath());
            bn.put("sourceLabel", bg.sourceLabel());
            bn.put("originXMm", bg.originXMm());
            bn.put("originYMm", bg.originYMm());
            bn.put("mmPerPixel", bg.mmPerPixel());
            bn.put("opacity", bg.opacity());
            if (bg.embeddedRef() != null && !bg.embeddedRef().isBlank()) {
                bn.put("embeddedRef", bg.embeddedRef());
            }
            if (bg.mediaHash() != null && !bg.mediaHash().isBlank()) {
                bn.put("mediaHash", bg.mediaHash());
            }
        }
        return n;
    }

    private void readFloorPlan(JsonNode n, FloorPlan fp) {
        if (n == null || n.isMissingNode()) {
            return;
        }
        fp.clearAll();
        if (n.has("gridMm")) {
            fp.setGridMm(n.get("gridMm").asDouble(500));
        }
        if (n.has("snapToGrid")) {
            fp.setSnapToGrid(n.get("snapToGrid").asBoolean(true));
        }

        JsonNode walls = n.path("walls");
        if (walls.isArray()) {
            for (JsonNode w : walls) {
                String id = text(w, "id", null);
                Vec2 a = new Vec2(w.path("x1").asDouble(), w.path("y1").asDouble());
                Vec2 b = new Vec2(w.path("x2").asDouble(), w.path("y2").asDouble());
                double th = w.path("thicknessMm").asDouble(150);
                if (id == null) {
                    fp.addWall(new Wall(a, b));
                } else {
                    fp.addWall(new Wall(id, a, b, th));
                }
            }
        }

        JsonNode rooms = n.path("rooms");
        if (rooms.isArray()) {
            for (JsonNode r : rooms) {
                String id = text(r, "id", null);
                String name = text(r, "name", "Room");
                double x = r.path("x").asDouble();
                double y = r.path("y").asDouble();
                double w = r.path("widthMm").asDouble(1000);
                double h = r.path("heightMm").asDouble(1000);
                if (id == null) {
                    fp.addRoom(new Room(name, x, y, w, h));
                } else {
                    fp.addRoom(new Room(id, name, x, y, w, h));
                }
            }
        }

        JsonNode openings = n.path("openings");
        if (openings.isArray()) {
            for (JsonNode o : openings) {
                String id = text(o, "id", null);
                String wallId = text(o, "wallId", null);
                if (wallId == null) {
                    continue;
                }
                OpeningType type = OpeningType.DOOR;
                try {
                    type = OpeningType.valueOf(text(o, "type", "DOOR"));
                } catch (IllegalArgumentException ignored) {
                    // default DOOR
                }
                double t = o.path("t").asDouble(0.5);
                double width = o.path("widthMm").asDouble(900);
                if (id == null) {
                    fp.addOpening(new Opening(wallId, type, t, width));
                } else {
                    fp.addOpening(new Opening(id, wallId, type, t, width));
                }
            }
        }

        JsonNode devices = n.path("devices");
        if (devices.isArray()) {
            for (JsonNode d : devices) {
                String id = text(d, "id", null);
                String componentId = text(d, "componentId", "UNKNOWN");
                String symbolKey = text(d, "symbolKey", "generic");
                String nameOverride = text(d, "nameOverride", null);
                double x = d.path("xMm").asDouble();
                double y = d.path("yMm").asDouble();
                double rot = d.path("rotationDeg").asDouble(0);
                String roomId = text(d, "roomId", null);
                String circuitId = text(d, "circuitId", null);
                double heightMm = d.path("mountingHeightMm").asDouble(0);
                if (id == null) {
                    PlacedDevice pd = new PlacedDevice(componentId, symbolKey, x, y);
                    pd.setNameOverride(nameOverride);
                    pd.setRotationDeg(rot);
                    pd.setRoomId(roomId);
                    pd.setCircuitId(circuitId);
                    pd.setMountingHeightMm(heightMm);
                    fp.addDevice(pd);
                } else {
                    fp.addDevice(new PlacedDevice(
                            id, componentId, symbolKey, nameOverride, x, y, rot, roomId,
                            circuitId, heightMm
                    ));
                }
            }
        }

        if (n.has("showWiringRoutes")) {
            fp.setShowWiringRoutes(n.get("showWiringRoutes").asBoolean(true));
        }
        JsonNode routes = n.path("wiringRoutes");
        if (routes.isArray()) {
            for (JsonNode rn : routes) {
                List<Vec2> pts = new ArrayList<>();
                JsonNode points = rn.path("points");
                if (points.isArray()) {
                    for (JsonNode p : points) {
                        pts.add(new Vec2(p.path("x").asDouble(), p.path("y").asDouble()));
                    }
                }
                WiringRoute wr = new WiringRoute(
                        text(rn, "id", null),
                        text(rn, "circuitId", null),
                        text(rn, "cableComponentId", null),
                        text(rn, "label", ""),
                        pts
                );
                fp.addWiringRoute(wr);
            }
        }

        JsonNode dims = n.path("dimensions");
        if (dims.isArray()) {
            for (JsonNode d : dims) {
                Vec2 p1 = new Vec2(d.path("x1").asDouble(), d.path("y1").asDouble());
                Vec2 p2 = new Vec2(d.path("x2").asDouble(), d.path("y2").asDouble());
                LinearDimension dim = new LinearDimension(
                        text(d, "id", null),
                        p1,
                        p2,
                        d.path("offsetMm").asDouble(400),
                        text(d, "labelOverride", null)
                );
                fp.addDimension(dim);
            }
        }

        JsonNode bg = n.path("background");
        if (bg.isObject() && bg.hasNonNull("sourcePath")) {
            BackgroundImage image = new BackgroundImage(
                    bg.get("sourcePath").asText(),
                    text(bg, "sourceLabel", bg.get("sourcePath").asText()),
                    bg.path("mmPerPixel").asDouble(10)
            );
            image.setOrigin(bg.path("originXMm").asDouble(0), bg.path("originYMm").asDouble(0));
            image.setOpacity(bg.path("opacity").asDouble(0.55));
            image.setEmbeddedRef(text(bg, "embeddedRef", null));
            image.setMediaHash(text(bg, "mediaHash", null));
            fp.setBackground(image);
        }
    }

    private ArrayNode writeCircuits(List<Circuit> circuits) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (Circuit c : circuits) {
            ObjectNode n = arr.addObject();
            n.put("id", c.id());
            n.put("name", c.name());
            n.put("kind", c.kind().name());
            if (c.roomId() != null) {
                n.put("roomId", c.roomId());
            }
            ArrayNode ids = n.putArray("deviceIds");
            for (String id : c.deviceIds()) {
                ids.add(id);
            }
            n.put("wayNumber", c.wayNumber());
            n.put("rcdGroup", c.rcdGroup());
            n.put("breakerA", c.breakerA());
            n.put("cableComponentId", c.cableComponentId());
            n.put("cableSize", c.cableSize());
            n.put("estimatedLengthM", c.estimatedLengthM());
            n.put("notes", c.notes());
        }
        return arr;
    }

    private void readCircuits(JsonNode arr, Project project) {
        if (arr == null || !arr.isArray()) {
            return;
        }
        List<Circuit> list = new ArrayList<>();
        for (JsonNode n : arr) {
            CircuitKind kind = CircuitKind.OTHER;
            try {
                kind = CircuitKind.valueOf(text(n, "kind", "OTHER"));
            } catch (IllegalArgumentException ignored) {
                // default
            }
            Circuit c = new Circuit(text(n, "id", null), text(n, "name", "Circuit"), kind, text(n, "roomId", null));
            JsonNode ids = n.path("deviceIds");
            if (ids.isArray()) {
                for (JsonNode id : ids) {
                    c.addDeviceId(id.asText());
                }
            }
            c.setWayNumber(n.path("wayNumber").asInt(0));
            c.setRcdGroup(text(n, "rcdGroup", ""));
            c.setBreakerA(n.path("breakerA").asDouble(0));
            c.setCableComponentId(text(n, "cableComponentId", ""));
            c.setCableSize(text(n, "cableSize", ""));
            c.setEstimatedLengthM(n.path("estimatedLengthM").asDouble(0));
            c.setNotes(text(n, "notes", ""));
            list.add(c);
        }
        project.setCircuits(list);
    }

    private ObjectNode writeConsumerUnit(ConsumerUnit cu) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("id", cu.id());
        n.put("name", cu.name());
        n.put("ways", cu.ways());
        n.put("incomerA", cu.incomerA());
        n.put("rcdDescription", cu.rcdDescription());
        ArrayNode ways = n.putArray("wayCircuitIds");
        for (String id : cu.wayCircuitIds()) {
            if (id == null) {
                ways.addNull();
            } else {
                ways.add(id);
            }
        }
        return n;
    }

    private void readConsumerUnit(JsonNode n, Project project) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return;
        }
        ConsumerUnit cu = new ConsumerUnit(
                text(n, "id", null),
                text(n, "name", "Main consumer unit"),
                n.path("ways").asInt(12),
                n.path("incomerA").asDouble(60),
                text(n, "rcdDescription", "RCCB 63 A 30 mA")
        );
        JsonNode ways = n.path("wayCircuitIds");
        if (ways.isArray()) {
            for (int i = 0; i < ways.size() && i < cu.ways(); i++) {
                JsonNode w = ways.get(i);
                cu.setWayCircuit(i, w == null || w.isNull() ? null : w.asText(null));
            }
        }
        project.setConsumerUnit(cu);
    }

    private ObjectNode writeChecklist(ChecklistReview review) {
        ObjectNode n = MAPPER.createObjectNode();
        for (var e : review.entries().entrySet()) {
            ObjectNode en = n.putObject(e.getKey());
            en.put("reviewed", e.getValue().reviewed());
            en.put("note", e.getValue().note());
        }
        return n;
    }

    private void readChecklist(JsonNode n, Project project) {
        if (n == null || n.isMissingNode() || !n.isObject()) {
            return;
        }
        n.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            project.checklistReview().setReviewed(
                    e.getKey(),
                    v.path("reviewed").asBoolean(false),
                    text(v, "note", "")
            );
        });
    }

    private static String text(JsonNode n, String field, String def) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return def;
        }
        String v = n.get(field).asText(def);
        return v == null || v.isBlank() ? def : v;
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
