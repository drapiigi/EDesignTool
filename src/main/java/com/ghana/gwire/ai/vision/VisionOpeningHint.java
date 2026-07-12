package com.ghana.gwire.ai.vision;

/**
 * Door/window along a wall, position as normalized image coordinates of the opening centre.
 */
public record VisionOpeningHint(String type, double xNorm, double yNorm, double widthNorm) {
    public VisionOpeningHint {
        if (type == null || type.isBlank()) {
            type = "DOOR";
        } else {
            type = type.trim().toUpperCase();
        }
        xNorm = Math.clamp(xNorm, 0, 1);
        yNorm = Math.clamp(yNorm, 0, 1);
        widthNorm = Math.clamp(widthNorm <= 0 ? 0.05 : widthNorm, 0.01, 0.3);
    }
}
