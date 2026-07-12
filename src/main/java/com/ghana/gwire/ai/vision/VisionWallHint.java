package com.ghana.gwire.ai.vision;

/**
 * Wall segment in normalized image coordinates (0..1).
 */
public record VisionWallHint(double x1Norm, double y1Norm, double x2Norm, double y2Norm) {
    public VisionWallHint {
        x1Norm = Math.clamp(x1Norm, 0, 1);
        y1Norm = Math.clamp(y1Norm, 0, 1);
        x2Norm = Math.clamp(x2Norm, 0, 1);
        y2Norm = Math.clamp(y2Norm, 0, 1);
    }
}
