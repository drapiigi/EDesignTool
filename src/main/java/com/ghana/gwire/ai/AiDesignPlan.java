package com.ghana.gwire.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Proposed electrical design placements from rules, an LLM, or a hybrid pipeline.
 */
public final class AiDesignPlan {

    public enum Source {
        RULES,
        LLM,
        HYBRID
    }

    private final Source source;
    private final String notes;
    private final List<DesignPlacement> placements;
    private final String providerDetail;

    public AiDesignPlan(Source source, String notes, List<DesignPlacement> placements, String providerDetail) {
        this.source = source == null ? Source.RULES : source;
        this.notes = notes == null ? "" : notes;
        this.placements = Collections.unmodifiableList(new ArrayList<>(
                placements == null ? List.of() : placements
        ));
        this.providerDetail = providerDetail == null ? "" : providerDetail;
    }

    public Source source() {
        return source;
    }

    public String notes() {
        return notes;
    }

    public List<DesignPlacement> placements() {
        return placements;
    }

    public String providerDetail() {
        return providerDetail;
    }

    public boolean isEmpty() {
        return placements.isEmpty();
    }

    public int size() {
        return placements.size();
    }

    /** Count placements whose componentId starts with {@code prefix} (case-sensitive). */
    public int countByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (DesignPlacement p : placements) {
            if (p.componentId().startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    /** Count placements whose componentId contains {@code fragment} (case-insensitive). */
    public int countContaining(String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return 0;
        }
        String f = fragment.toUpperCase(Locale.ROOT);
        int n = 0;
        for (DesignPlacement p : placements) {
            if (p.componentId().toUpperCase(Locale.ROOT).contains(f)) {
                n++;
            }
        }
        return n;
    }

    public AiDesignPlan withSource(Source newSource, String newProviderDetail) {
        return new AiDesignPlan(newSource, notes, placements, newProviderDetail);
    }

    public AiDesignPlan withNotes(String newNotes) {
        return new AiDesignPlan(source, newNotes, placements, providerDetail);
    }

    @Override
    public String toString() {
        return "AiDesignPlan{source=" + source
                + ", placements=" + placements.size()
                + ", provider=" + providerDetail + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiDesignPlan that)) {
            return false;
        }
        return source == that.source
                && Objects.equals(notes, that.notes)
                && Objects.equals(placements, that.placements)
                && Objects.equals(providerDetail, that.providerDetail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, notes, placements, providerDetail);
    }
}
