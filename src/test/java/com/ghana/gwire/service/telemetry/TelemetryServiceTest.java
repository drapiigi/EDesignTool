package com.ghana.gwire.service.telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServiceTest {

    @TempDir
    Path temp;

    @Test
    void disabledIsNoOp() throws Exception {
        Path log = temp.resolve("telemetry.log");
        TelemetryService tel = new TelemetryService(log);
        assertFalse(tel.isEnabled());
        tel.record(TelemetryService.EVENT_APP_START);
        tel.record(TelemetryService.EVENT_CALC_RUN, Map.of("circuits", "3"));
        assertFalse(Files.exists(log), "disabled telemetry must not create log file");
    }

    @Test
    void enabledWritesJsonLine() throws Exception {
        Path log = temp.resolve("telemetry.log");
        TelemetryService tel = new TelemetryService(log);
        tel.setEnabled(true);
        assertTrue(tel.isEnabled());

        tel.record(TelemetryService.EVENT_EXPORT_PDF, Map.of("pages", "2"));

        assertTrue(Files.isRegularFile(log));
        String content = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"event\":\"export.pdf\""), content);
        assertTrue(content.contains("\"pages\":\"2\""), content);
        assertTrue(content.contains("\"ts\":"), content);
    }

    @Test
    void setEnabledFalseStopsWriting() throws Exception {
        Path log = temp.resolve("telemetry.log");
        TelemetryService tel = new TelemetryService(log);
        tel.setEnabled(true);
        tel.record(TelemetryService.EVENT_APP_START);
        long sizeAfterOne = Files.size(log);

        tel.setEnabled(false);
        tel.record(TelemetryService.EVENT_CALC_RUN);
        assertTrue(Files.size(log) == sizeAfterOne, "disabled after enable must not append");
    }
}
