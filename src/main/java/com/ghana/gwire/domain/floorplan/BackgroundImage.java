package com.ghana.gwire.domain.floorplan;

import java.util.Objects;

/**
 * Imported floor-plan raster (image or PDF page) positioned in plan space.
 * Pixel → mm conversion uses {@link #mmPerPixel} for scale calibration.
 */
public final class BackgroundImage {

    private final String sourcePath;
    private final String sourceLabel;
    private double originXMm;
    private double originYMm;
    private double mmPerPixel;
    private double opacity;
    /** Optional package media entry id ({@code media/{embeddedRef}}). */
    private String embeddedRef;
    /** Optional SHA-256 hex of embedded bytes. */
    private String mediaHash;

    public BackgroundImage(String sourcePath, String sourceLabel, double mmPerPixel) {
        this.sourcePath = Objects.requireNonNull(sourcePath);
        this.sourceLabel = sourceLabel == null ? sourcePath : sourceLabel;
        this.originXMm = 0;
        this.originYMm = 0;
        this.mmPerPixel = mmPerPixel > 0 ? mmPerPixel : 10;
        this.opacity = 0.55;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public String embeddedRef() {
        return embeddedRef;
    }

    public void setEmbeddedRef(String embeddedRef) {
        this.embeddedRef = embeddedRef == null || embeddedRef.isBlank() ? null : embeddedRef.trim();
    }

    public String mediaHash() {
        return mediaHash;
    }

    public void setMediaHash(String mediaHash) {
        this.mediaHash = mediaHash == null || mediaHash.isBlank() ? null : mediaHash.trim();
    }

    public double originXMm() {
        return originXMm;
    }

    public double originYMm() {
        return originYMm;
    }

    public void setOrigin(double xMm, double yMm) {
        this.originXMm = xMm;
        this.originYMm = yMm;
    }

    public double mmPerPixel() {
        return mmPerPixel;
    }

    public void setMmPerPixel(double mmPerPixel) {
        if (mmPerPixel > 0) {
            this.mmPerPixel = mmPerPixel;
        }
    }

    public double opacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.clamp(opacity, 0.1, 1.0);
    }

    public BackgroundImage copy() {
        BackgroundImage copy = new BackgroundImage(sourcePath, sourceLabel, mmPerPixel);
        copy.setOrigin(originXMm, originYMm);
        copy.setOpacity(opacity);
        copy.setEmbeddedRef(embeddedRef);
        copy.setMediaHash(mediaHash);
        return copy;
    }
}
