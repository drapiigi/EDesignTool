package com.ghana.gwire.service.importing;

import javafx.scene.image.Image;

/**
 * Result of importing a floor-plan image or PDF page.
 */
public record ImportedRaster(
        Image image,
        String sourcePath,
        String label,
        int pixelWidth,
        int pixelHeight,
        /** Suggested mm per pixel so a typical A3 plan fits ~20 m width. */
        double suggestedMmPerPixel
) {
}
