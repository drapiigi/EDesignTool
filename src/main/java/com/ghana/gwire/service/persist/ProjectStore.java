package com.ghana.gwire.service.persist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.BackgroundImage;
import com.ghana.gwire.domain.floorplan.FloorPlan;
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
 * Load/save GhanaWire projects as JSON ({@code .gwire} files).
 *
 * <p>Format version 1 stores geometry, devices, settings, and background path.
 * Calculation reports are not persisted (re-run Tools → Recalculate Loads).
 */
public final class ProjectStore {

    /** 1.0 single floor; 1.1 multi-storey + wiring routes (still loads 1.0 files). */
    public static final String FORMAT_VERSION = "1.1";
    public static final String FILE_EXTENSION = "gwire";

    private static final Logger log = LoggerFactory.getLogger(ProjectStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void save(Project project, Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");
        project.touch();
        ObjectNode root = MAPPER.createObjectNode();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("app", "GhanaWire AI");
        root.put("id", project.id());
        root.put("name", project.name());
        root.put("createdAt", project.createdAt().toString());
        root.put("modifiedAt", project.modifiedAt().toString());
        root.set("settings", writeSettings(project.settings()));
        // Backward-compatible single floorPlan = active storey snapshot
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

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writeValue(path.toFile(), root);
        log.info("Saved project '{}' to {} ({} storeys)", project.name(), path, project.storeys().size());
    }

    public Project load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Project file not found: " + path);
        }
        JsonNode root = MAPPER.readTree(path.toFile());
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
        // lastReport is not persisted; leave null
        log.info("Loaded project '{}' from {} ({} storeys, {} rooms, {} devices)",
                project.name(), path,
                project.storeys().size(),
                project.totalRoomCount(),
                project.totalDeviceCount());
        return project;
    }

    private ObjectNode writeSettings(ProjectSettings s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("houseType", s.houseType());
        n.put("supplyType", s.supplyType().name());
        n.put("nominalVoltageV", s.nominalVoltageV());
        n.put("frequencyHz", s.frequencyHz());
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

        if (fp.background() != null) {
            BackgroundImage bg = fp.background();
            ObjectNode bn = n.putObject("background");
            bn.put("sourcePath", bg.sourcePath());
            bn.put("sourceLabel", bg.sourceLabel());
            bn.put("originXMm", bg.originXMm());
            bn.put("originYMm", bg.originYMm());
            bn.put("mmPerPixel", bg.mmPerPixel());
            bn.put("opacity", bg.opacity());
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
                if (id == null) {
                    PlacedDevice pd = new PlacedDevice(componentId, symbolKey, x, y);
                    pd.setNameOverride(nameOverride);
                    pd.setRotationDeg(rot);
                    pd.setRoomId(roomId);
                    fp.addDevice(pd);
                } else {
                    fp.addDevice(new PlacedDevice(
                            id, componentId, symbolKey, nameOverride, x, y, rot, roomId
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

        JsonNode bg = n.path("background");
        if (bg.isObject() && bg.hasNonNull("sourcePath")) {
            BackgroundImage image = new BackgroundImage(
                    bg.get("sourcePath").asText(),
                    text(bg, "sourceLabel", bg.get("sourcePath").asText()),
                    bg.path("mmPerPixel").asDouble(10)
            );
            image.setOrigin(bg.path("originXMm").asDouble(0), bg.path("originYMm").asDouble(0));
            image.setOpacity(bg.path("opacity").asDouble(0.55));
            fp.setBackground(image);
        }
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
