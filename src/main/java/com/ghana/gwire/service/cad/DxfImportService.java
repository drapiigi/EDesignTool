package com.ghana.gwire.service.cad;

import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal DXF import (Phase 13b): LINE and LWPOLYLINE → walls on the active floor plan.
 * Supports common R12-style pair lists (group code / value).
 */
public final class DxfImportService {

    private static final Logger log = LoggerFactory.getLogger(DxfImportService.class);

    public int importWalls(Project project, Path path, boolean clearExistingWalls) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");
        String text = Files.readString(path, StandardCharsets.UTF_8);
        List<Wall> walls = parseWalls(text);
        FloorPlan fp = project.floorPlan();
        if (clearExistingWalls) {
            // Remove walls only; keep rooms/devices
            List<Wall> existing = new ArrayList<>(fp.walls());
            for (Wall w : existing) {
                fp.removeWallById(w.id());
            }
        }
        for (Wall w : walls) {
            if (w.lengthMm() >= 50) {
                fp.addWall(w);
            }
        }
        project.touch();
        log.info("DXF import: {} wall(s) from {}", walls.size(), path.getFileName());
        return walls.size();
    }

    public List<Wall> parseWalls(String dxfText) {
        List<String> tokens = tokenize(dxfText);
        List<Wall> walls = new ArrayList<>();
        int i = 0;
        while (i + 1 < tokens.size()) {
            String code = tokens.get(i).trim();
            String value = tokens.get(i + 1).trim();
            i += 2;
            if (!"0".equals(code)) {
                continue;
            }
            String type = value.toUpperCase(Locale.ROOT);
            if ("LINE".equals(type)) {
                Double x1 = null, y1 = null, x2 = null, y2 = null;
                while (i + 1 < tokens.size()) {
                    String c = tokens.get(i).trim();
                    String v = tokens.get(i + 1).trim();
                    if ("0".equals(c)) {
                        break;
                    }
                    i += 2;
                    switch (c) {
                        case "10" -> x1 = parseD(v);
                        case "20" -> y1 = parseD(v);
                        case "11" -> x2 = parseD(v);
                        case "21" -> y2 = parseD(v);
                        default -> {
                        }
                    }
                }
                if (x1 != null && y1 != null && x2 != null && y2 != null) {
                    walls.add(new Wall(new Vec2(x1, y1), new Vec2(x2, y2)));
                }
            } else if ("LWPOLYLINE".equals(type)) {
                List<Vec2> pts = new ArrayList<>();
                boolean closed = false;
                Double pendingX = null;
                while (i + 1 < tokens.size()) {
                    String c = tokens.get(i).trim();
                    String v = tokens.get(i + 1).trim();
                    if ("0".equals(c)) {
                        break;
                    }
                    i += 2;
                    switch (c) {
                        case "70" -> {
                            Double flags = parseD(v);
                            closed = flags != null && (flags.intValue() & 1) == 1;
                        }
                        case "10" -> pendingX = parseD(v);
                        case "20" -> {
                            if (pendingX != null) {
                                Double y = parseD(v);
                                if (y != null) {
                                    pts.add(new Vec2(pendingX, y));
                                }
                                pendingX = null;
                            }
                        }
                        default -> {
                        }
                    }
                }
                for (int p = 0; p + 1 < pts.size(); p++) {
                    walls.add(new Wall(pts.get(p), pts.get(p + 1)));
                }
                if (closed && pts.size() >= 2) {
                    walls.add(new Wall(pts.get(pts.size() - 1), pts.get(0)));
                }
            }
        }
        return walls;
    }

    private static List<String> tokenize(String text) {
        String[] lines = text.split("\\R");
        List<String> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(line);
        }
        return out;
    }

    private static Double parseD(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
