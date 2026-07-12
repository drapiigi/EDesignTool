package com.ghana.gwire.service.importing;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Loads floor-plan rasters from common image formats and PDF (first page).
 */
public class FloorPlanImportService {

    private static final Logger log = LoggerFactory.getLogger(FloorPlanImportService.class);

    private static final Set<String> IMAGE_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp"
    );

    /** Target plan width in mm when auto-scaling import (~20 m façade). */
    private static final double TARGET_PLAN_WIDTH_MM = 20_000;

    public boolean isSupported(Path path) {
        String ext = extension(path);
        return IMAGE_EXT.contains(ext) || "pdf".equals(ext);
    }

    public ImportedRaster importFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a file: " + path);
        }
        String ext = extension(path);
        if ("pdf".equals(ext)) {
            return importPdf(path);
        }
        if (IMAGE_EXT.contains(ext)) {
            return importImage(path);
        }
        throw new IOException("Unsupported floor plan format: " + ext);
    }

    private ImportedRaster importImage(Path path) throws IOException {
        BufferedImage buffered = javax.imageio.ImageIO.read(path.toFile());
        if (buffered == null) {
            throw new IOException("Failed to load image: " + path);
        }
        Image image = SwingFXUtils.toFXImage(buffered, null);
        int w = buffered.getWidth();
        int h = buffered.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Image has zero size: " + path);
        }
        double mmPerPx = TARGET_PLAN_WIDTH_MM / w;
        log.info("Imported image {} ({}x{}) mm/px={}", path.getFileName(), w, h, mmPerPx);
        return new ImportedRaster(image, path.toAbsolutePath().toString(),
                path.getFileName().toString(), w, h, mmPerPx);
    }

    private ImportedRaster importPdf(Path path) throws IOException {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            if (doc.getNumberOfPages() < 1) {
                throw new IOException("PDF has no pages: " + path);
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            // 150 DPI is a good balance for tracing walls
            BufferedImage buffered = renderer.renderImageWithDPI(0, 150, ImageType.RGB);
            Image image = SwingFXUtils.toFXImage(buffered, null);
            int w = buffered.getWidth();
            int h = buffered.getHeight();
            double mmPerPx = TARGET_PLAN_WIDTH_MM / w;
            log.info("Imported PDF page 1 of {} ({}x{})", path.getFileName(), w, h);
            return new ImportedRaster(image, path.toAbsolutePath().toString(),
                    path.getFileName().toString() + " (page 1)", w, h, mmPerPx);
        }
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
