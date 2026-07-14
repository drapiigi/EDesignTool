package com.ghana.gwire.service.export;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.calc.ValidationIssue;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.floorplan.FloorPlan;
import com.ghana.gwire.domain.floorplan.Room;
import com.ghana.gwire.domain.floorplan.Wall;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.calc.CalcEngine;
import com.ghana.gwire.service.sld.SingleLineDiagram;
import com.ghana.gwire.service.sld.SingleLineDiagramBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Multi-page PDF export for GhanaWire projects (Phase 7).
 *
 * <p>Pages: cover, floor plan, circuit schedule, BOQ, compliance checklist.
 * Preliminary design report only — CEWP verification required for installations.
 */
public final class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);
    private static final float MARGIN = 48f;
    /** Baseline skip for body text (10 pt + leading) — keeps lines from overlapping. */
    private static final float BODY_LEADING = 14f;
    private static final float SMALL_LEADING = 11f;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CalcEngine calcEngine = new CalcEngine();
    private final PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * Exports a full report PDF. Runs calculation if project has no lastReport.
     */
    public void export(Project project, Path output) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(output, "output");

        DesignReport report = project.lastReport();
        if (report == null) {
            ComponentLibraryService lib = null;
            try {
                lib = LibraryBootstrap.get();
            } catch (Exception ignored) {
                // optional
            }
            report = calcEngine.calculate(project, lib);
            project.setLastReport(report);
        }

        try (PDDocument doc = new PDDocument()) {
            writeCoverPage(doc, project, report);
            writeFloorPlanPage(doc, project);
            writeSldPage(doc, project, report);
            writeCircuitSchedulePage(doc, project, report);
            writeBoqPage(doc, project, report);
            writeChecklistPage(doc, project, report);
            if (output.getParent() != null) {
                java.nio.file.Files.createDirectories(output.getParent());
            }
            doc.save(output.toFile());
        }
        log.info("Exported PDF report for '{}' to {}", project.name(), output);
    }

    private void writeCoverPage(PDDocument doc, Project project, DesignReport report) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = page.getMediaBox().getHeight() - MARGIN;
            y = title(cs, "GhanaWire AI - Design Report", y);
            y = gap(y, 6);
            y = heading(cs, project.name(), y);
            y = gap(y, 8);
            y = line(cs, "Generated: " + LocalDateTime.now().format(TS), y);
            y = line(cs, "Supply: " + project.supplySummary(), y);
            y = line(cs, "House type: " + project.settings().houseType(), y);
            String standards = report.standardsEdition().isBlank()
                    ? project.settings().standardsEdition() : report.standardsEdition();
            y = line(cs, "Standards pack: " + standards
                    + " | app " + com.ghana.gwire.GWireApp.APP_VERSION, y);
            if (report.calculatedAtExport()) {
                y = line(cs, "Note: Calculated at export (" + formatInstant(report.calculatedAt()) + ")", y);
            }
            y = line(cs, String.format(Locale.ROOT,
                    "Storeys: %d | rooms: %d | devices: %d (all floors)",
                    project.storeys().size(),
                    project.totalRoomCount(),
                    project.totalDeviceCount()), y);
            y = gap(y, 12);
            y = section(cs, "Design summary (after diversity)", y);
            y = line(cs, String.format(Locale.ROOT, "Connected load: %.0f W", report.totalConnectedLoadW()), y);
            y = line(cs, String.format(Locale.ROOT, "After diversity: %.0f W | Design current: %.1f A",
                    report.totalAfterDiversityW(), report.totalDesignCurrentA()), y);
            y = line(cs, String.format(Locale.ROOT, "Circuits: %d | Max voltage drop: %.2f%%",
                    report.circuits().size(), report.maxVoltageDropPercent()), y);
            y = line(cs, String.format(Locale.ROOT, "Validation: %d error(s), %d warning(s)",
                    report.errorCount(), report.warningCount()), y);
            if (!report.assumptions().isEmpty()) {
                y = gap(y, 10);
                y = section(cs, "Assumptions (codes)", y);
                int shown = 0;
                for (String code : report.assumptions()) {
                    if (y < MARGIN + 120) {
                        y = line(cs, "... +" + (report.assumptions().size() - shown) + " more (see app)", y);
                        break;
                    }
                    if (shown >= 14) {
                        y = line(cs, "... +" + (report.assumptions().size() - shown) + " more (see app)", y);
                        break;
                    }
                    y = line(cs, "- " + code, y);
                    shown++;
                }
            }
            y = gap(y, 14);
            y = section(cs, "Contents", y);
            y = line(cs, "1. Cover & summary", y);
            y = line(cs, "2. Floor plan layout (active storey)", y);
            y = line(cs, "3. Single-line diagram", y);
            y = line(cs, "4. Circuit schedule", y);
            y = line(cs, "5. Bill of quantities", y);
            y = line(cs, "6. Compliance checklist (L.I. 2008 practice)", y);
            footer(cs, page, "Preliminary design - verify with a CEWP before installation.");
        }
    }

    private void writeFloorPlanPage(PDDocument doc, Project project) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float pageW = page.getMediaBox().getWidth();
        float pageH = page.getMediaBox().getHeight();
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = pageH - MARGIN;
            y = title(cs, "Floor plan layout", y);
            y = gap(y, 4);
            y = line(cs, project.name() + " - rooms, walls, devices (plan mm)", y);

            FloorPlan fp = project.floorPlan();
            Bounds b = bounds(fp);
            float plotX = MARGIN;
            float plotY = MARGIN + 40;
            float plotW = pageW - 2 * MARGIN;
            float plotH = y - 24 - plotY;

            cs.setStrokingColor(Color.LIGHT_GRAY);
            cs.addRect(plotX, plotY, plotW, plotH);
            cs.stroke();

            if (b.width() > 1 && b.height() > 1) {
                // Prefer standard architectural plot scales 1:50 or 1:100 (mm paper / mm plan)
                // A4 content width ~500 pt ≈ 176 mm; pick scale so plan fits with margin.
                float paperWidthMm = (plotW / 72f) * 25.4f;
                float planW = (float) b.width();
                int plotRatio = (planW / paperWidthMm) <= 55 ? 50 : 100;
                // pts per plan-mm at 1:N: (72/25.4) / N
                float scaleAtRatio = (72f / 25.4f) / plotRatio;
                float fitScale = Math.min(plotW / (float) b.width(), plotH / (float) b.height()) * 0.90f;
                float scale = Math.min(scaleAtRatio, fitScale);
                // If fit forces smaller than 1:100, report as fitted
                if (scale < scaleAtRatio * 0.98f) {
                    plotRatio = Math.max(50, Math.round((72f / 25.4f) / scale));
                    // nice round 50/100/200
                    if (plotRatio <= 60) {
                        plotRatio = 50;
                    } else if (plotRatio <= 150) {
                        plotRatio = 100;
                    } else {
                        plotRatio = 200;
                    }
                    scale = Math.min((72f / 25.4f) / plotRatio, fitScale);
                }

                float ox = plotX + (plotW - (float) b.width() * scale) / 2f;
                float oy = plotY + (plotH - (float) b.height() * scale) / 2f;

                // Rooms
                cs.setNonStrokingColor(new Color(0.85f, 0.92f, 0.98f));
                cs.setStrokingColor(new Color(0.2f, 0.4f, 0.7f));
                for (Room r : fp.rooms()) {
                    float x = ox + (float) ((r.x() - b.minX) * scale);
                    float ry = oy + (float) ((r.y() - b.minY) * scale);
                    float w = (float) (r.widthMm() * scale);
                    float h = (float) (r.heightMm() * scale);
                    cs.addRect(x, ry, w, h);
                    cs.fillAndStroke();
                }

                // Walls
                cs.setStrokingColor(Color.DARK_GRAY);
                cs.setLineWidth(1.5f);
                for (Wall wall : fp.walls()) {
                    float x1 = ox + (float) ((wall.start().x() - b.minX) * scale);
                    float y1 = oy + (float) ((wall.start().y() - b.minY) * scale);
                    float x2 = ox + (float) ((wall.end().x() - b.minX) * scale);
                    float y2 = oy + (float) ((wall.end().y() - b.minY) * scale);
                    cs.moveTo(x1, y1);
                    cs.lineTo(x2, y2);
                    cs.stroke();
                }

                // Devices
                cs.setNonStrokingColor(new Color(0.15f, 0.55f, 0.35f));
                for (PlacedDevice d : fp.devices()) {
                    float x = ox + (float) ((d.xMm() - b.minX) * scale);
                    float dy = oy + (float) ((d.yMm() - b.minY) * scale);
                    cs.addRect(x - 2.5f, dy - 2.5f, 5, 5);
                    cs.fill();
                }

                // Room labels
                cs.setNonStrokingColor(Color.BLACK);
                for (Room r : fp.rooms()) {
                    float x = ox + (float) ((r.x() - b.minX) * scale) + 4;
                    float ry = oy + (float) ((r.y() - b.minY + r.heightMm() * 0.5) * scale);
                    textAt(cs, fontRegular, 8, x, ry, safe(r.name()));
                }

                drawPlotScaleBar(cs, plotX + 8, plotY + 10, scale, plotRatio);
            } else {
                textAt(cs, fontRegular, 11, plotX + 20, plotY + plotH / 2,
                        "No geometry to draw - add rooms or devices first.");
            }

            textAt(cs, fontRegular, 8, MARGIN, MARGIN + 16,
                    "Legend: blue rectangles = rooms | dark lines = walls | green squares = devices");
            footer(cs, page, "GhanaWire AI - floor plan");
        }
    }

    /**
     * Architectural scale bar for plot page (true length in plan mm at the drawn scale).
     */
    private void drawPlotScaleBar(PDPageContentStream cs, float x, float y, float ptsPerPlanMm, int ratio)
            throws IOException {
        // Prefer 5 m or 2 m bar
        double barMm = 5000;
        float barPts = (float) (barMm * ptsPerPlanMm);
        if (barPts > 180) {
            barMm = 2000;
            barPts = (float) (barMm * ptsPerPlanMm);
        }
        if (barPts < 40) {
            barMm = 10000;
            barPts = (float) (barMm * ptsPerPlanMm);
        }
        cs.setStrokingColor(Color.BLACK);
        cs.setNonStrokingColor(Color.BLACK);
        cs.setLineWidth(1f);
        cs.moveTo(x, y);
        cs.lineTo(x + barPts, y);
        cs.stroke();
        cs.moveTo(x, y - 4);
        cs.lineTo(x, y + 4);
        cs.stroke();
        cs.moveTo(x + barPts, y - 4);
        cs.lineTo(x + barPts, y + 4);
        cs.stroke();
        int segs = 4;
        for (int i = 0; i < segs; i++) {
            float sx = x + i * (barPts / segs);
            float w = barPts / segs;
            if (i % 2 == 0) {
                cs.addRect(sx, y, w, 3);
                cs.fill();
            }
        }
        String label = barMm >= 1000
                ? String.format(Locale.ROOT, "1:%d  |  %.0f m", ratio, barMm / 1000.0)
                : String.format(Locale.ROOT, "1:%d  |  %.0f mm", ratio, barMm);
        textAt(cs, fontRegular, 8, x, y + 10, label);
    }

    private void writeSldPage(PDDocument doc, Project project, DesignReport report) throws IOException {
        SingleLineDiagram sld = new SingleLineDiagramBuilder().build(project, report);
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = page.getMediaBox().getHeight() - MARGIN;
            y = title(cs, "Single-line diagram", y);
            y = gap(y, 4);
            y = line(cs, sld.title(), y);
            y = line(cs, "Schematic only - not a certified SLD", y);
            y = gap(y, 10);
            y = drawSldNode(cs, sld.root(), MARGIN, y, 0);
            y = gap(y, 8);
            y = wrapped(cs, sld.notes(), y, 9);
            footer(cs, page, "GhanaWire AI - single-line diagram");
        }
    }

    private float drawSldNode(PDPageContentStream cs, SingleLineDiagram.Node node, float x, float y, int depth)
            throws IOException {
        if (y < MARGIN + 40) {
            return y;
        }
        String indent = "  ".repeat(Math.min(depth, 8));
        String kind = node.kind().name();
        textAt(cs, fontBold, 10, x, y, indent + "+ " + kind + ": " + safe(node.label()));
        y -= BODY_LEADING;
        if (!node.detail().isBlank()) {
            textAt(cs, fontRegular, 8, x + 12, y, indent + "  " + safe(node.detail()));
            y -= SMALL_LEADING;
        }
        y -= 2;
        for (SingleLineDiagram.Node child : node.children()) {
            y = drawSldNode(cs, child, x, y, depth + 1);
        }
        return y;
    }

    private void writeCircuitSchedulePage(PDDocument doc, Project project, DesignReport report)
            throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = page.getMediaBox().getHeight() - MARGIN;
            y = title(cs, "Circuit schedule", y);
            y = gap(y, 4);
            y = line(cs, "Project: " + project.name() + " | " + project.supplySummary(), y);
            y = gap(y, 8);

            String[] headers = {"Circuit", "Kind", "P (W)", "I (A)", "Cable", "MCB", "Vd %", "L (m)"};
            float[] cols = {120, 70, 50, 45, 70, 40, 40, 45};
            y = tableHeader(cs, headers, cols, MARGIN, y);

            if (report.circuits().isEmpty()) {
                y = line(cs, "No circuits calculated.", y);
            } else {
                for (CircuitLoad c : report.circuits()) {
                    if (y < MARGIN + 60) {
                        y = line(cs, "... additional circuits omitted (see app for full list)", y);
                        break;
                    }
                    String[] row = {
                            truncate(c.name(), 22),
                            c.kind().name(),
                            String.format(Locale.ROOT, "%.0f", c.connectedLoadW()),
                            String.format(Locale.ROOT, "%.1f", c.designCurrentA()),
                            truncate(nullToDash(c.recommendedCableSize()), 12),
                            c.recommendedBreakerA() > 0
                                    ? String.format(Locale.ROOT, "%.0fA", c.recommendedBreakerA()) : "-",
                            String.format(Locale.ROOT, "%.2f", c.voltageDropPercent()),
                            String.format(Locale.ROOT, "%.1f", c.estimatedLengthM())
                    };
                    y = tableRow(cs, row, cols, MARGIN, y);
                }
            }
            footer(cs, page, "GhanaWire AI - circuit schedule");
        }
    }

    private void writeBoqPage(PDDocument doc, Project project, DesignReport report) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = page.getMediaBox().getHeight() - MARGIN;
            y = title(cs, "Bill of quantities", y);
            y = gap(y, 4);
            y = line(cs, "Currency: GHS | device counts + estimated circuit cables", y);
            y = gap(y, 8);

            ComponentLibraryService lib = safeLib();
            List<String[]> rows = new ArrayList<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (var storey : project.storeys()) {
                for (PlacedDevice d : storey.floorPlan().devices()) {
                    counts.merge(d.componentId(), 1, Integer::sum);
                }
            }
            double grand = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                Optional<ElectricalComponent> c = lib == null ? Optional.empty() : lib.getById(e.getKey());
                String name = c.map(ElectricalComponent::name).orElse(e.getKey());
                String unit = c.map(ElectricalComponent::unit).orElse("pcs");
                double unitCost = c.map(ElectricalComponent::unitCostGhs).orElse(0.0);
                double total = unitCost * e.getValue();
                grand += total;
                rows.add(new String[]{
                        truncate(name, 36),
                        String.valueOf(e.getValue()),
                        unit,
                        String.format(Locale.ROOT, "%.2f", unitCost),
                        String.format(Locale.ROOT, "%.2f", total)
                });
            }
            for (DesignReport.CableBoqLine line : report.cableBoq()) {
                Optional<ElectricalComponent> c = lib == null ? Optional.empty() : lib.getById(line.componentId());
                String name = c.map(ElectricalComponent::name)
                        .orElse(line.description().isBlank() ? line.componentId() : line.description());
                double unitCost = c.map(ElectricalComponent::unitCostGhs).orElse(0.0);
                double total = unitCost * line.lengthM();
                grand += total;
                rows.add(new String[]{
                        truncate(name + " (est.)", 36),
                        String.format(Locale.ROOT, "%.1f", line.lengthM()),
                        "m",
                        String.format(Locale.ROOT, "%.2f", unitCost),
                        String.format(Locale.ROOT, "%.2f", total)
                });
            }

            String[] headers = {"Item", "Qty", "Unit", "Unit cost", "Total"};
            float[] cols = {220, 50, 40, 70, 70};
            y = tableHeader(cs, headers, cols, MARGIN, y);
            if (rows.isEmpty()) {
                y = line(cs, "No BOQ lines - place devices or recalculate loads.", y);
            } else {
                for (String[] row : rows) {
                    if (y < MARGIN + 50) {
                        break;
                    }
                    y = tableRow(cs, row, cols, MARGIN, y);
                }
            }
            y = gap(y, 10);
            y = line(cs, String.format(Locale.ROOT, "Subtotal: GHS %.2f", grand), y);
            footer(cs, page, "GhanaWire AI - BOQ");
        }
    }

    private void writeChecklistPage(PDDocument doc, Project project, DesignReport report)
            throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = page.getMediaBox().getHeight() - MARGIN;
            y = title(cs, "Compliance checklist", y);
            y = gap(y, 4);
            y = line(cs, "Illustrative L.I. 2008 / good-practice checks - not a certificate", y);
            y = gap(y, 8);

            y = section(cs, "Automatic checks from calculation engine", y);
            if (report.issues().isEmpty()) {
                y = line(cs, "No automatic issues raised for the current model.", y);
            } else {
                for (ValidationIssue issue : report.issues()) {
                    if (y < MARGIN + 80) {
                        break;
                    }
                    String mark = switch (issue.severity()) {
                        case ERROR -> "[ERR]";
                        case WARNING -> "[WRN]";
                        case INFO -> "[INF]";
                    };
                    boolean reviewed = project.checklistReview().isReviewed(issue.code());
                    String rev = reviewed ? " [reviewed]" : " [open]";
                    String note = project.checklistReview().note(issue.code());
                    y = gap(y, 4);
                    y = wrapped(cs, mark + rev + " " + issue.code() + ": " + issue.message(), y, 9);
                    if (note != null && !note.isBlank()) {
                        y = wrapped(cs, "  Note: " + note, y, 9);
                    }
                }
            }

            y = gap(y, 14);
            y = section(cs, "Manual CEWP review (tick when verified)", y);
            String[] manual = {
                    "[ ] Cable installation methods match site (clipped, conduit, buried)",
                    "[ ] RCD/RCBO protection for socket circuits confirmed",
                    "[ ] Earthing / bonding arrangement verified on site",
                    "[ ] Consumer unit way count and spare capacity adequate",
                    "[ ] Special locations (bathrooms, kitchens) comply with L.I. 2008",
                    "[ ] Diversity and load assessment accepted by CEWP",
                    "[ ] As-built drawings updated after installation"
            };
            for (String m : manual) {
                y = line(cs, m, y);
            }

            y = gap(y, 14);
            y = line(cs, "Prepared with GhanaWire AI. Signature / stamp: ____________________  Date: ________", y);
            footer(cs, page, "GhanaWire AI - compliance checklist");
        }
    }

    // --- drawing helpers ---

    private static float gap(float y, float dy) {
        return y - dy;
    }

    private float title(PDPageContentStream cs, String text, float y) throws IOException {
        textAt(cs, fontBold, 16, MARGIN, y, text);
        return y - 22;
    }

    private float heading(PDPageContentStream cs, String text, float y) throws IOException {
        textAt(cs, fontBold, 13, MARGIN, y, text);
        return y - 18;
    }

    private float section(PDPageContentStream cs, String text, float y) throws IOException {
        textAt(cs, fontBold, 11, MARGIN, y, text);
        return y - 16;
    }

    /**
     * Draw a body line at baseline {@code y} and return the next baseline
     * (leading {@link #BODY_LEADING}). Callers must pass the current baseline only —
     * do not pre-subtract small offsets (that caused overlapping text).
     */
    private float line(PDPageContentStream cs, String text, float y) throws IOException {
        textAt(cs, fontRegular, 10, MARGIN, y, text);
        return y - BODY_LEADING;
    }

    private float textAt(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        String s = safe(text);
        if (s.isEmpty()) {
            return y;
        }
        // Clip to printable page width to avoid PDFBox issues with huge strings
        float maxWidth = PDRectangle.A4.getWidth() - MARGIN - x;
        s = fitWidth(font, size, s, maxWidth);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
        return y;
    }

    private float wrapped(PDPageContentStream cs, String text, float y, float size) throws IOException {
        String s = safe(text);
        int max = 90;
        float leading = size + 4f;
        while (!s.isEmpty() && y > MARGIN + 40) {
            String chunk;
            if (s.length() <= max) {
                chunk = s;
                s = "";
            } else {
                int cut = s.lastIndexOf(' ', max);
                if (cut < 20) {
                    cut = max;
                }
                chunk = s.substring(0, cut);
                s = s.substring(cut).trim();
            }
            textAt(cs, fontRegular, size, MARGIN, y, chunk);
            y -= leading;
        }
        return y;
    }

    private float tableHeader(PDPageContentStream cs, String[] headers, float[] cols, float x, float y)
            throws IOException {
        float xx = x;
        for (int i = 0; i < headers.length; i++) {
            textAt(cs, fontBold, 9, xx, y, headers[i]);
            xx += cols[i];
        }
        cs.setStrokingColor(Color.GRAY);
        cs.moveTo(x, y - 4);
        cs.lineTo(x + sum(cols), y - 4);
        cs.stroke();
        return y - 16;
    }

    private float tableRow(PDPageContentStream cs, String[] cells, float[] cols, float x, float y)
            throws IOException {
        float xx = x;
        for (int i = 0; i < cells.length && i < cols.length; i++) {
            textAt(cs, fontRegular, 8, xx, y, cells[i]);
            xx += cols[i];
        }
        return y - 13;
    }

    private void footer(PDPageContentStream cs, PDPage page, String text) throws IOException {
        textAt(cs, fontRegular, 8, MARGIN, 28, text);
    }

    private static float sum(float[] a) {
        float s = 0;
        for (float v : a) {
            s += v;
        }
        return s;
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TS);
    }

    /**
     * Convert text to WinAnsi/Helvetica-safe ASCII so PDFBox does not render
     * missing glyphs as "?" or throw encoding errors.
     */
    static String safe(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\n', '\r', '\t' -> sb.append(' ');
                case '\u00A0' -> sb.append(' '); // nbsp
                case '\u2013', '\u2014', '\u2212' -> sb.append('-'); // en/em/minus
                case '\u2018', '\u2019', '\u201A' -> sb.append('\'');
                case '\u201C', '\u201D', '\u201E' -> sb.append('"');
                case '\u2022', '\u00B7', '\u2219' -> sb.append('-'); // bullets / middot
                case '\u2026' -> sb.append("...");
                case '\u00D7', '\u2715', '\u2716' -> sb.append('x');
                case '\u00F7' -> sb.append('/');
                case '\u2264' -> sb.append("<=");
                case '\u2265' -> sb.append(">=");
                case '\u2192', '\u21D2' -> sb.append("->");
                case '\u00B0' -> sb.append(" deg");
                case '\u00B2' -> sb.append("2"); // mm2
                case '\u00B3' -> sb.append("3");
                case '\u00BD' -> sb.append("1/2");
                case '\u2126', '\u03A9' -> sb.append("ohm");
                case '\u00B1' -> sb.append("+/-");
                default -> {
                    if (c >= 32 && c <= 126) {
                        sb.append(c);
                    } else if (c >= 160 && c <= 255) {
                        // Common Latin-1: map a few remaining awkward glyphs
                        sb.append(switch (c) {
                            case '\u00A9' -> "(c)";
                            case '\u00AE' -> "(R)";
                            case '\u00B5' -> 'u';
                            default -> '?'; // remaining Latin-1 rarely needed
                        });
                    } else {
                        // Drop other Unicode (emoji, CJK, etc.) rather than litter '?'
                    }
                }
            }
        }
        // Collapse runs of spaces
        return sb.toString().replaceAll(" +", " ").trim();
    }

    private static String fitWidth(PDType1Font font, float size, String text, float maxWidth)
            throws IOException {
        if (maxWidth <= 0 || text.isEmpty()) {
            return text;
        }
        float w = font.getStringWidth(text) / 1000f * size;
        if (w <= maxWidth) {
            return text;
        }
        String ell = "...";
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String candidate = text.substring(0, mid) + ell;
            float cw = font.getStringWidth(candidate) / 1000f * size;
            if (cw <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo <= 0 ? ell : text.substring(0, lo) + ell;
    }

    private static String truncate(String s, int max) {
        s = safe(s);
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 3)) + "...";
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : safe(s);
    }

    private static ComponentLibraryService safeLib() {
        try {
            return LibraryBootstrap.get();
        } catch (Exception e) {
            return null;
        }
    }

    private record Bounds(double minX, double minY, double maxX, double maxY) {
        double width() {
            return Math.max(1, maxX - minX);
        }

        double height() {
            return Math.max(1, maxY - minY);
        }
    }

    private static Bounds bounds(FloorPlan fp) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (Room r : fp.rooms()) {
            minX = Math.min(minX, r.x());
            minY = Math.min(minY, r.y());
            maxX = Math.max(maxX, r.x() + r.widthMm());
            maxY = Math.max(maxY, r.y() + r.heightMm());
            any = true;
        }
        for (Wall w : fp.walls()) {
            minX = Math.min(minX, Math.min(w.start().x(), w.end().x()));
            minY = Math.min(minY, Math.min(w.start().y(), w.end().y()));
            maxX = Math.max(maxX, Math.max(w.start().x(), w.end().x()));
            maxY = Math.max(maxY, Math.max(w.start().y(), w.end().y()));
            any = true;
        }
        for (PlacedDevice d : fp.devices()) {
            minX = Math.min(minX, d.xMm());
            minY = Math.min(minY, d.yMm());
            maxX = Math.max(maxX, d.xMm());
            maxY = Math.max(maxY, d.yMm());
            any = true;
        }
        if (!any) {
            return new Bounds(0, 0, 10000, 8000);
        }
        double pad = 500;
        return new Bounds(minX - pad, minY - pad, maxX + pad, maxY + pad);
    }
}
