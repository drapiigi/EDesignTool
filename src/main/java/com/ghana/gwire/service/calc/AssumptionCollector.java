package com.ghana.gwire.service.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Collects unique assumption codes during a calculation run (sorted at finish).
 */
public final class AssumptionCollector {

    private final TreeSet<String> codes = new TreeSet<>();

    public void add(String code) {
        if (code != null && !code.isBlank()) {
            codes.add(code.trim());
        }
    }

    public boolean isEmpty() {
        return codes.isEmpty();
    }

    /** Sorted unique codes. */
    public List<String> sorted() {
        return Collections.unmodifiableList(new ArrayList<>(codes));
    }

    public static AssumptionCollector nullSafe(AssumptionCollector c) {
        return c == null ? new AssumptionCollector() : c;
    }
}
