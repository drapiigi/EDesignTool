package com.ghana.gwire.domain.calc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable-ish aggregate result of a design calculation run.
 * Collections are mutable only via the builder-style setters used by {@code CalcEngine}.
 */
public final class DesignReport {

    /**
     * Aggregated cable BOQ line for recommended circuit cables.
     */
    public record CableBoqLine(String componentId, double lengthM, String description) {
        public CableBoqLine {
            if (componentId == null) {
                componentId = "";
            }
            lengthM = Math.max(0, lengthM);
            if (description == null) {
                description = "";
            }
        }
    }

    private String projectName = "Untitled project";
    private Instant calculatedAt = Instant.now();
    private double supplyVoltageV = 230;
    private String supplyTypeSummary = "";
    private double totalConnectedLoadW;
    private double totalAfterDiversityW;
    private double totalDesignCurrentA;
    private double diversityApplied = 1.0;
    private final List<CircuitLoad> circuits = new ArrayList<>();
    private final List<ValidationIssue> issues = new ArrayList<>();
    private final List<CableBoqLine> cableBoq = new ArrayList<>();
    private final List<String> assumptions = new ArrayList<>();
    private double maxVoltageDropPercent;
    /** e.g. L.I. 2008 practice tables v2026.1 */
    private String standardsEdition = "";
    /** True when report was produced automatically at export time. */
    private boolean calculatedAtExport;

    public String projectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName == null || projectName.isBlank() ? "Untitled project" : projectName;
    }

    public Instant calculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt == null ? Instant.now() : calculatedAt;
    }

    public double supplyVoltageV() {
        return supplyVoltageV;
    }

    public void setSupplyVoltageV(double supplyVoltageV) {
        this.supplyVoltageV = supplyVoltageV;
    }

    public String supplyTypeSummary() {
        return supplyTypeSummary;
    }

    public void setSupplyTypeSummary(String supplyTypeSummary) {
        this.supplyTypeSummary = supplyTypeSummary == null ? "" : supplyTypeSummary;
    }

    public double totalConnectedLoadW() {
        return totalConnectedLoadW;
    }

    public void setTotalConnectedLoadW(double totalConnectedLoadW) {
        this.totalConnectedLoadW = Math.max(0, totalConnectedLoadW);
    }

    public double totalAfterDiversityW() {
        return totalAfterDiversityW;
    }

    public void setTotalAfterDiversityW(double totalAfterDiversityW) {
        this.totalAfterDiversityW = Math.max(0, totalAfterDiversityW);
    }

    public double totalDesignCurrentA() {
        return totalDesignCurrentA;
    }

    public void setTotalDesignCurrentA(double totalDesignCurrentA) {
        this.totalDesignCurrentA = Math.max(0, totalDesignCurrentA);
    }

    public double diversityApplied() {
        return diversityApplied;
    }

    public void setDiversityApplied(double diversityApplied) {
        this.diversityApplied = Math.max(0, diversityApplied);
    }

    public List<CircuitLoad> circuits() {
        return Collections.unmodifiableList(circuits);
    }

    public void setCircuits(List<CircuitLoad> circuits) {
        this.circuits.clear();
        if (circuits != null) {
            this.circuits.addAll(circuits);
        }
    }

    public void addCircuit(CircuitLoad circuit) {
        if (circuit != null) {
            circuits.add(circuit);
        }
    }

    public List<ValidationIssue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public void setIssues(List<ValidationIssue> issues) {
        this.issues.clear();
        if (issues != null) {
            this.issues.addAll(issues);
        }
    }

    public void addIssue(ValidationIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    public List<CableBoqLine> cableBoq() {
        return Collections.unmodifiableList(cableBoq);
    }

    public void setCableBoq(List<CableBoqLine> lines) {
        cableBoq.clear();
        if (lines != null) {
            cableBoq.addAll(lines);
        }
    }

    public void addCableBoq(CableBoqLine line) {
        if (line != null) {
            cableBoq.add(line);
        }
    }

    public double maxVoltageDropPercent() {
        return maxVoltageDropPercent;
    }

    public void setMaxVoltageDropPercent(double maxVoltageDropPercent) {
        this.maxVoltageDropPercent = Math.max(0, maxVoltageDropPercent);
    }

    public int errorCount() {
        return (int) issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
    }

    public int warningCount() {
        return (int) issues.stream().filter(i -> i.severity() == Severity.WARNING).count();
    }

    public boolean hasErrors() {
        return errorCount() > 0;
    }

    public List<String> assumptions() {
        return Collections.unmodifiableList(assumptions);
    }

    public void setAssumptions(List<String> assumptions) {
        this.assumptions.clear();
        if (assumptions != null) {
            this.assumptions.addAll(assumptions);
        }
    }

    public void addAssumption(String code) {
        if (code != null && !code.isBlank() && !assumptions.contains(code)) {
            assumptions.add(code);
        }
    }

    public String standardsEdition() {
        return standardsEdition;
    }

    public void setStandardsEdition(String standardsEdition) {
        this.standardsEdition = standardsEdition == null ? "" : standardsEdition;
    }

    public boolean calculatedAtExport() {
        return calculatedAtExport;
    }

    public void setCalculatedAtExport(boolean calculatedAtExport) {
        this.calculatedAtExport = calculatedAtExport;
    }

    @Override
    public String toString() {
        return "DesignReport{" +
                "project='" + projectName + '\'' +
                ", circuits=" + circuits.size() +
                ", issues=" + issues.size() +
                ", assumptions=" + assumptions.size() +
                ", totalW=" + totalAfterDiversityW +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DesignReport that)) {
            return false;
        }
        return Objects.equals(projectName, that.projectName)
                && Objects.equals(calculatedAt, that.calculatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, calculatedAt);
    }
}
