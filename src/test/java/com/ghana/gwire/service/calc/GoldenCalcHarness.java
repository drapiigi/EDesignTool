package com.ghana.gwire.service.calc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.DesignReport;
import com.ghana.gwire.domain.calc.ValidationIssue;

import java.util.Comparator;
import java.util.List;

/**
 * Normalizes {@link DesignReport} for golden-file comparison (strip UUIDs / timestamps).
 */
public final class GoldenCalcHarness {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private GoldenCalcHarness() {
    }

    public static String normalizeJson(DesignReport report) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("standardsEdition", report.standardsEdition());
        root.put("supplyVoltageV", round1(report.supplyVoltageV()));
        root.put("totalConnectedLoadW", round1(report.totalConnectedLoadW()));
        root.put("totalAfterDiversityW", round1(report.totalAfterDiversityW()));
        root.put("totalDesignCurrentA", round2(report.totalDesignCurrentA()));
        root.put("diversityApplied", round3(report.diversityApplied()));
        root.put("maxVoltageDropPercent", round2(report.maxVoltageDropPercent()));

        ArrayNode circuits = root.putArray("circuits");
        List<CircuitLoad> sorted = report.circuits().stream()
                .sorted(Comparator
                        .comparing((CircuitLoad c) -> c.kind().name())
                        .thenComparing(CircuitLoad::name)
                        .thenComparingDouble(CircuitLoad::connectedLoadW))
                .toList();
        for (CircuitLoad c : sorted) {
            ObjectNode cn = circuits.addObject();
            cn.put("kind", c.kind().name());
            cn.put("name", c.name());
            cn.put("connectedLoadW", round1(c.connectedLoadW()));
            cn.put("designCurrentA", round2(c.designCurrentA()));
            cn.put("diversityFactor", round3(c.diversityFactor()));
            cn.put("afterDiversityLoadW", round1(c.afterDiversityLoadW()));
            cn.put("estimatedLengthM", round2(c.estimatedLengthM()));
            cn.put("recommendedCableSize", nullToEmpty(c.recommendedCableSize()));
            cn.put("recommendedBreakerA", round1(c.recommendedBreakerA()));
            cn.put("voltageDropPercent", round2(c.voltageDropPercent()));
            cn.put("deviceCount", c.deviceIds().size());
        }

        ArrayNode issues = root.putArray("issues");
        report.issues().stream()
                .sorted(Comparator
                        .comparing((ValidationIssue i) -> i.severity().name())
                        .thenComparing(ValidationIssue::code)
                        .thenComparing(ValidationIssue::message))
                .forEach(i -> {
                    ObjectNode in = issues.addObject();
                    in.put("severity", i.severity().name());
                    in.put("code", i.code());
                });

        ArrayNode assumptions = root.putArray("assumptions");
        report.assumptions().forEach(assumptions::add);

        return MAPPER.writeValueAsString(root);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
