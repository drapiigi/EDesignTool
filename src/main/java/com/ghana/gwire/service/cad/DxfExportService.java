package com.ghana.gwire.service.cad;

import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.LinearDimension;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.geometry.Vec2;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Minimal DXF R12-style geometry export (Phase 13b): walls as LINE, rooms as LWPOLYLINE.
 * Coordinates are plan millimetres. Electrical devices are not exported in MVP.
 */
public final class DxfExportService {

    public void export(Project project, Path path) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(path, "path");
        String dxf = toDxf(project);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, dxf, StandardCharsets.US_ASCII);
    }

    public String toDxf(Project project) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("0\nSECTION\n2\nHEADER\n");
        sb.append("9\n$INSUNITS\n70\n4\n"); // millimetres
        sb.append("0\nENDSEC\n");
        sb.append("0\nSECTION\n2\nENTITIES\n");

        for (BuildingStorey storey : project.storeys()) {
            String layer = "WALLS_" + sanitize(storey.name());
            FloorPlan fp = storey.floorPlan();
            for (Wall w : fp.walls()) {
                line(sb, layer, w.start(), w.end());
            }
            for (Room r : fp.rooms()) {
                lwpolyRect(sb, "ROOMS_" + sanitize(storey.name()),
                        r.x(), r.y(), r.widthMm(), r.heightMm());
            }
            for (LinearDimension d : fp.dimensions()) {
                line(sb, "DIMS", d.p1(), d.p2());
            }
        }

        sb.append("0\nENDSEC\n0\nEOF\n");
        return sb.toString();
    }

    private static void line(StringBuilder sb, String layer, Vec2 a, Vec2 b) {
        sb.append("0\nLINE\n8\n").append(layer).append('\n');
        sb.append("10\n").append(fmt(a.x())).append('\n');
        sb.append("20\n").append(fmt(a.y())).append('\n');
        sb.append("30\n0.0\n");
        sb.append("11\n").append(fmt(b.x())).append('\n');
        sb.append("21\n").append(fmt(b.y())).append('\n');
        sb.append("31\n0.0\n");
    }

    private static void lwpolyRect(StringBuilder sb, String layer, double x, double y, double w, double h) {
        sb.append("0\nLWPOLYLINE\n8\n").append(layer).append('\n');
        sb.append("90\n4\n70\n1\n"); // closed
        vertex(sb, x, y);
        vertex(sb, x + w, y);
        vertex(sb, x + w, y + h);
        vertex(sb, x, y + h);
    }

    private static void vertex(StringBuilder sb, double x, double y) {
        sb.append("10\n").append(fmt(x)).append('\n');
        sb.append("20\n").append(fmt(y)).append('\n');
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.3f", v);
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) {
            return "0";
        }
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
