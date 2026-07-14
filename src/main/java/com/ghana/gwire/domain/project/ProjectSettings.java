package com.ghana.gwire.domain.project;

/**
 * Project-level electrical context (Ghana defaults).
 * Expanded in calculation phases; Phase 2 stores supply context only.
 */
public final class ProjectSettings {

    public enum SupplyType {
        SINGLE_PHASE_230V,
        THREE_PHASE_400V
    }

    private String houseType = "Residential";
    private SupplyType supplyType = SupplyType.SINGLE_PHASE_230V;
    private double nominalVoltageV = 230;
    private double frequencyHz = 50;
    /** Standards pack label stamped on DesignReport (not a full legal edition). */
    private String standardsEdition = "L.I. 2008 practice tables v2026.1";

    public String houseType() {
        return houseType;
    }

    public void setHouseType(String houseType) {
        this.houseType = houseType == null || houseType.isBlank() ? "Residential" : houseType;
    }

    public SupplyType supplyType() {
        return supplyType;
    }

    public void setSupplyType(SupplyType supplyType) {
        this.supplyType = supplyType == null ? SupplyType.SINGLE_PHASE_230V : supplyType;
        this.nominalVoltageV = this.supplyType == SupplyType.THREE_PHASE_400V ? 400 : 230;
    }

    public double nominalVoltageV() {
        return nominalVoltageV;
    }

    public double frequencyHz() {
        return frequencyHz;
    }

    public String standardsEdition() {
        return standardsEdition;
    }

    public void setStandardsEdition(String standardsEdition) {
        this.standardsEdition = standardsEdition == null || standardsEdition.isBlank()
                ? "L.I. 2008 practice tables v2026.1"
                : standardsEdition.trim();
    }

    public ProjectSettings copy() {
        ProjectSettings s = new ProjectSettings();
        s.houseType = houseType;
        s.supplyType = supplyType;
        s.nominalVoltageV = nominalVoltageV;
        s.frequencyHz = frequencyHz;
        s.standardsEdition = standardsEdition;
        return s;
    }
}
