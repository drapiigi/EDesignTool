package com.ghana.gwire.domain.components;

import java.util.Objects;

/**
 * Catalogue entry for an electrical product/symbol used in Ghana domestic wiring.
 * Immutable value object; costs may be updated via repository (new instance or DB row).
 *
 * <p>Cable electrical parameters (ratings, mV/A/m) are typical/illustrative values
 * following common BS/IEC practice for sizing and will be refined in Phase 4.
 */
public final class ElectricalComponent {

    private final String id;
    private final String name;
    private final ComponentCategory category;
    private final String description;
    private final String ghanaReference;
    private final String standardSize;
    private final String unit;
    private final double unitCostGhs;
    private final String symbolKey;
    private final Double currentRatingA;
    private final Double voltageRatingV;
    private final Integer poles;
    private final Double crossSectionMm2;
    private final Double resistanceOhmPerKm;
    private final Double voltageDropMvPerAm;
    private final Double powerW;
    private final String notes;
    private final boolean active;

    private ElectricalComponent(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.name = Objects.requireNonNull(b.name, "name");
        this.category = Objects.requireNonNull(b.category, "category");
        this.description = b.description;
        this.ghanaReference = b.ghanaReference;
        this.standardSize = b.standardSize;
        this.unit = b.unit == null || b.unit.isBlank() ? "pcs" : b.unit;
        this.unitCostGhs = b.unitCostGhs;
        this.symbolKey = Objects.requireNonNull(b.symbolKey, "symbolKey");
        this.currentRatingA = b.currentRatingA;
        this.voltageRatingV = b.voltageRatingV;
        this.poles = b.poles;
        this.crossSectionMm2 = b.crossSectionMm2;
        this.resistanceOhmPerKm = b.resistanceOhmPerKm;
        this.voltageDropMvPerAm = b.voltageDropMvPerAm;
        this.powerW = b.powerW;
        this.notes = b.notes;
        this.active = b.active;
    }

    public static Builder builder(String id, String name, ComponentCategory category, String symbolKey) {
        return new Builder(id, name, category, symbolKey);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ComponentCategory category() {
        return category;
    }

    public String description() {
        return description;
    }

    public String ghanaReference() {
        return ghanaReference;
    }

    public String standardSize() {
        return standardSize;
    }

    public String unit() {
        return unit;
    }

    public double unitCostGhs() {
        return unitCostGhs;
    }

    public String symbolKey() {
        return symbolKey;
    }

    public Double currentRatingA() {
        return currentRatingA;
    }

    public Double voltageRatingV() {
        return voltageRatingV;
    }

    public Integer poles() {
        return poles;
    }

    public Double crossSectionMm2() {
        return crossSectionMm2;
    }

    public Double resistanceOhmPerKm() {
        return resistanceOhmPerKm;
    }

    public Double voltageDropMvPerAm() {
        return voltageDropMvPerAm;
    }

    public Double powerW() {
        return powerW;
    }

    public String notes() {
        return notes;
    }

    public boolean active() {
        return active;
    }

    /** Returns a copy with an updated unit cost (GHS). */
    public ElectricalComponent withUnitCostGhs(double cost) {
        return toBuilder().unitCostGhs(cost).build();
    }

    public Builder toBuilder() {
        return new Builder(id, name, category, symbolKey)
                .description(description)
                .ghanaReference(ghanaReference)
                .standardSize(standardSize)
                .unit(unit)
                .unitCostGhs(unitCostGhs)
                .currentRatingA(currentRatingA)
                .voltageRatingV(voltageRatingV)
                .poles(poles)
                .crossSectionMm2(crossSectionMm2)
                .resistanceOhmPerKm(resistanceOhmPerKm)
                .voltageDropMvPerAm(voltageDropMvPerAm)
                .powerW(powerW)
                .notes(notes)
                .active(active);
    }

    @Override
    public String toString() {
        return name + " [" + id + "]";
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final ComponentCategory category;
        private final String symbolKey;
        private String description;
        private String ghanaReference;
        private String standardSize;
        private String unit = "pcs";
        private double unitCostGhs;
        private Double currentRatingA;
        private Double voltageRatingV;
        private Integer poles;
        private Double crossSectionMm2;
        private Double resistanceOhmPerKm;
        private Double voltageDropMvPerAm;
        private Double powerW;
        private String notes;
        private boolean active = true;

        private Builder(String id, String name, ComponentCategory category, String symbolKey) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.symbolKey = symbolKey;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder ghanaReference(String ghanaReference) {
            this.ghanaReference = ghanaReference;
            return this;
        }

        public Builder standardSize(String standardSize) {
            this.standardSize = standardSize;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder unitCostGhs(double unitCostGhs) {
            this.unitCostGhs = unitCostGhs;
            return this;
        }

        public Builder currentRatingA(Double currentRatingA) {
            this.currentRatingA = currentRatingA;
            return this;
        }

        public Builder voltageRatingV(Double voltageRatingV) {
            this.voltageRatingV = voltageRatingV;
            return this;
        }

        public Builder poles(Integer poles) {
            this.poles = poles;
            return this;
        }

        public Builder crossSectionMm2(Double crossSectionMm2) {
            this.crossSectionMm2 = crossSectionMm2;
            return this;
        }

        public Builder resistanceOhmPerKm(Double resistanceOhmPerKm) {
            this.resistanceOhmPerKm = resistanceOhmPerKm;
            return this;
        }

        public Builder voltageDropMvPerAm(Double voltageDropMvPerAm) {
            this.voltageDropMvPerAm = voltageDropMvPerAm;
            return this;
        }

        public Builder powerW(Double powerW) {
            this.powerW = powerW;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public ElectricalComponent build() {
            return new ElectricalComponent(this);
        }
    }
}
