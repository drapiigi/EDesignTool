package com.ghana.gwire.ai.vision;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Loads floor-plan rasters, downscales for vision APIs, and base64-encodes as PNG/JPEG.
 */
public final class ImageEncoder {

    /** Max edge length sent to vision models (API cost / payload). */
    public static final int DEFAULT_MAX_EDGE = 1600;

    public record EncodedImage(
            byte[] bytes,
            String mediaType,
            String base64,
            int width,
            int height,
            int originalWidth,
            int originalHeight
    ) {
        public String dataUrl() {
            return "data:" + mediaType + ";base64," + base64;
        }
    }

    private ImageEncoder() {
    }

    public static EncodedImage encodeFile(Path path) throws IOException {
        return encodeFile(path, DEFAULT_MAX_EDGE);
    }

    public static EncodedImage encodeFile(Path path, int maxEdge) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a file: " + path);
        }
        BufferedImage src = ImageIO.read(path.toFile());
        if (src == null) {
            throw new IOException("Could not decode image: " + path);
        }
        return encodeBuffered(src, maxEdge, preferJpeg(path) ? "image/jpeg" : "image/png");
    }

    public static EncodedImage encodeBuffered(BufferedImage src, int maxEdge, String preferredType)
            throws IOException {
        int ow = src.getWidth();
        int oh = src.getHeight();
        BufferedImage scaled = scaleDown(src, maxEdge);
        String mediaType = preferredType == null ? "image/png" : preferredType;
        String format = mediaType.contains("jpeg") || mediaType.contains("jpg") ? "jpg" : "png";
        if ("jpg".equals(format) && scaled.getType() == BufferedImage.TYPE_INT_ARGB) {
            BufferedImage rgb = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(scaled, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();
            scaled = rgb;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (!ImageIO.write(scaled, format, bos)) {
            // fallback PNG
            bos.reset();
            ImageIO.write(scaled, "png", bos);
            mediaType = "image/png";
            format = "png";
        }
        byte[] bytes = bos.toByteArray();
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return new EncodedImage(bytes, mediaType, b64, scaled.getWidth(), scaled.getHeight(), ow, oh);
    }

    static BufferedImage scaleDown(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int edge = Math.max(w, h);
        if (edge <= maxEdge || maxEdge <= 0) {
            return src;
        }
        double s = (double) maxEdge / edge;
        int nw = Math.max(1, (int) Math.round(w * s));
        int nh = Math.max(1, (int) Math.round(h * s));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static boolean preferJpeg(Path path) {
        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg");
    }
}
