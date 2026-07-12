package com.ghana.gwire.ai.vision;

/**
 * Room detected by vision as normalized fractions of the source image (0..1).
 * Converted to plan millimetres using background origin and mm-per-pixel scale.
 */
public record VisionRoomHint(
        String name,
        double xNorm,
        double yNorm,
        double widthNorm,
        double heightNorm,
        String roomType
) {
    public VisionRoomHint {
        if (name == null || name.isBlank()) {
            name = "Room";
        }
        xNorm = clamp01(xNorm);
        yNorm = clamp01(yNorm);
        widthNorm = Math.clamp(widthNorm, 0.01, 1.0);
        heightNorm = Math.clamp(heightNorm, 0.01, 1.0);
        if (xNorm + widthNorm > 1.0) {
            widthNorm = 1.0 - xNorm;
        }
        if (yNorm + heightNorm > 1.0) {
            heightNorm = 1.0 - yNorm;
        }
        if (roomType == null) {
            roomType = "";
        }
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0.0, 1.0);
    }
}
