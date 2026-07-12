package com.ghana.gwire.ai;

/**
 * A single proposed device placement from an AI / rule-based design plan (plan millimetres).
 *
 * @param componentId catalogue id (e.g. {@code SOCK-13A-2G})
 * @param symbolKey   glyph key for rendering
 * @param name        display label
 * @param xMm         plan X (mm)
 * @param yMm         plan Y (mm)
 * @param roomId      owning room id, or null for global devices
 * @param rotationDeg rotation in degrees
 */
public record DesignPlacement(
        String componentId,
        String symbolKey,
        String name,
        double xMm,
        double yMm,
        String roomId,
        double rotationDeg
) {
    public DesignPlacement {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId required");
        }
        if (symbolKey == null || symbolKey.isBlank()) {
            symbolKey = componentId;
        }
        if (name == null || name.isBlank()) {
            name = componentId;
        }
    }

    public DesignPlacement(String componentId, String symbolKey, String name, double xMm, double yMm, String roomId) {
        this(componentId, symbolKey, name, xMm, yMm, roomId, 0);
    }
}
