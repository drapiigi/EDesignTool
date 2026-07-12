package com.ghana.gwire.service.export;

import com.ghana.gwire.db.ComponentLibraryService;
import com.ghana.gwire.db.LibraryBootstrap;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.project.BuildingStorey;
import com.ghana.gwire.domain.project.Project;
import com.ghana.gwire.service.calc.CalcEngine;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Exports Bill of Quantities only as an Excel workbook (.xlsx).
 */
public final class BoqExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(BoqExcelExportService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CalcEngine calcEngine = new CalcEngine();

    /**
     * Writes a single-sheet BOQ workbook. Optionally ensures calc report exists for cable lines.
     *
     * @param includeCableEstimates if true and no lastReport, runs calculation first
     */
    public void export(Project project, Path output, boolean includeCableEstimates) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(output, "output");

        DesignReport report = project.lastReport();
        if (includeCableEstimates && report == null) {
            ComponentLibraryService lib = safeLib();
            report = calcEngine.calculate(project, lib);
            project.setLastReport(report);
        }

        ComponentLibraryService lib = safeLib();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("BOQ");

            CellStyle headerStyle = headerStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            CellStyle titleStyle = titleStyle(wb);
            CellStyle metaStyle = metaStyle(wb);

            int r = 0;
            Row title = sheet.createRow(r++);
            cell(title, 0, "GhanaWire AI — Bill of Quantities", titleStyle);
            Row meta1 = sheet.createRow(r++);
            cell(meta1, 0, "Project:", metaStyle);
            cell(meta1, 1, project.name(), metaStyle);
            Row meta2 = sheet.createRow(r++);
            cell(meta2, 0, "Supply:", metaStyle);
            cell(meta2, 1, project.supplySummary(), metaStyle);
            Row meta3 = sheet.createRow(r++);
            cell(meta3, 0, "Exported:", metaStyle);
            cell(meta3, 1, LocalDateTime.now().format(TS), metaStyle);
            Row meta4 = sheet.createRow(r++);
            cell(meta4, 0, "Currency:", metaStyle);
            cell(meta4, 1, "GHS", metaStyle);
            r++; // blank

            Row header = sheet.createRow(r++);
            String[] headers = {
                    "Item", "Component ID", "Category", "Qty", "Unit", "Unit cost (GHS)", "Total (GHS)", "Source"
            };
            for (int i = 0; i < headers.length; i++) {
                cell(header, i, headers[i], headerStyle);
            }

            double grand = 0;
            int dataStart = r;

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (BuildingStorey storey : project.storeys()) {
                for (PlacedDevice d : storey.floorPlan().devices()) {
                    counts.merge(d.componentId(), 1, Integer::sum);
                }
            }

            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                String id = e.getKey();
                int qty = e.getValue();
                Optional<ElectricalComponent> comp = lib == null ? Optional.empty() : lib.getById(id);
                String name = comp.map(ElectricalComponent::name).orElse(id);
                String unit = comp.map(ElectricalComponent::unit).orElse("pcs");
                String category = comp.map(c -> c.category().name()).orElse("");
                double unitCost = comp.map(ElectricalComponent::unitCostGhs).orElse(0.0);
                double total = unitCost * qty;
                grand += total;

                Row row = sheet.createRow(r++);
                cell(row, 0, name, null);
                cell(row, 1, id, null);
                cell(row, 2, category, null);
                cell(row, 3, qty, null);
                cell(row, 4, unit, null);
                cell(row, 5, unitCost, moneyStyle);
                cell(row, 6, total, moneyStyle);
                cell(row, 7, "Placed device", null);
            }

            if (report != null) {
                for (DesignReport.CableBoqLine line : report.cableBoq()) {
                    Optional<ElectricalComponent> cable =
                            lib == null ? Optional.empty() : lib.getById(line.componentId());
                    String name = cable.map(ElectricalComponent::name)
                            .orElse(line.description().isBlank() ? line.componentId() : line.description());
                    String category = cable.map(c -> c.category().name()).orElse("CABLE");
                    double unitCost = cable.map(ElectricalComponent::unitCostGhs).orElse(0.0);
                    double length = line.lengthM();
                    double total = unitCost * length;
                    grand += total;

                    Row row = sheet.createRow(r++);
                    cell(row, 0, name + " (circuit est.)", null);
                    cell(row, 1, line.componentId(), null);
                    cell(row, 2, category, null);
                    cell(row, 3, length, moneyStyle);
                    cell(row, 4, "m", null);
                    cell(row, 5, unitCost, moneyStyle);
                    cell(row, 6, total, moneyStyle);
                    cell(row, 7, "Calc cable estimate", null);
                }
            }

            r++;
            Row totalRow = sheet.createRow(r);
            cell(totalRow, 5, "Subtotal", headerStyle);
            cell(totalRow, 6, grand, moneyStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(width + 512, 18000));
            }

            if (dataStart == r - 1 && counts.isEmpty()
                    && (report == null || report.cableBoq().isEmpty())) {
                Row empty = sheet.createRow(dataStart);
                cell(empty, 0, "No BOQ lines — place devices or recalculate loads.", null);
            }

            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            try (OutputStream out = Files.newOutputStream(output)) {
                wb.write(out);
            }
        }
        log.info("Exported BOQ Excel for '{}' to {}", project.name(), output);
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private static CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private static CellStyle metaStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        return s;
    }

    private static CellStyle moneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return s;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static void cell(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static void cell(Row row, int col, int value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static ComponentLibraryService safeLib() {
        try {
            return LibraryBootstrap.get();
        } catch (Exception e) {
            return null;
        }
    }
}
