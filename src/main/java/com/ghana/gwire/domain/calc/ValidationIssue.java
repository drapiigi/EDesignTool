package com.ghana.gwire.domain.calc;

/**
 * A single standards or design check result from the calculation engine.
 *
 * @param severity         INFO / WARNING / ERROR
 * @param code             machine-readable code (e.g. {@code NO_RCD}, {@code VD_EXCEEDED})
 * @param message          human-readable explanation (may reference L.I. 2008 practice)
 * @param relatedDeviceId  optional placed-device id
 * @param relatedRoomId    optional room id
 */
public record ValidationIssue(
        Severity severity,
        String code,
        String message,
        String relatedDeviceId,
        String relatedRoomId
) {
    public ValidationIssue {
        if (severity == null) {
            severity = Severity.INFO;
        }
        if (code == null || code.isBlank()) {
            code = "UNKNOWN";
        }
        if (message == null) {
            message = "";
        }
    }

    public static ValidationIssue of(Severity severity, String code, String message) {
        return new ValidationIssue(severity, code, message, null, null);
    }

    public static ValidationIssue of(
            Severity severity,
            String code,
            String message,
            String relatedDeviceId,
            String relatedRoomId
    ) {
        return new ValidationIssue(severity, code, message, relatedDeviceId, relatedRoomId);
    }
}
