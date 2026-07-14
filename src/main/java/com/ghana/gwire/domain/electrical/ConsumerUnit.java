package com.ghana.gwire.domain.electrical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domestic consumer unit / distribution board model (Phase 14).
 */
public final class ConsumerUnit {

    private final String id;
    private String name;
    private int ways;
    private double incomerA;
    private String rcdDescription;
    /** Ordered circuit ids assigned to ways (index 0 = way 1). */
    private final List<String> wayCircuitIds = new ArrayList<>();

    public ConsumerUnit(String name, int ways) {
        this(UUID.randomUUID().toString(), name, ways, 60, "RCCB 63 A 30 mA");
    }

    public ConsumerUnit(String id, String name, int ways, double incomerA, String rcdDescription) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name == null || name.isBlank() ? "Main consumer unit" : name;
        this.ways = Math.max(4, ways);
        this.incomerA = incomerA > 0 ? incomerA : 60;
        this.rcdDescription = rcdDescription == null ? "RCCB 63 A 30 mA" : rcdDescription;
        ensureWaySlots();
    }

    private void ensureWaySlots() {
        while (wayCircuitIds.size() < ways) {
            wayCircuitIds.add(null);
        }
        while (wayCircuitIds.size() > ways) {
            wayCircuitIds.remove(wayCircuitIds.size() - 1);
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "Main consumer unit" : name;
    }

    public int ways() {
        return ways;
    }

    public void setWays(int ways) {
        this.ways = Math.max(4, ways);
        ensureWaySlots();
    }

    public double incomerA() {
        return incomerA;
    }

    public void setIncomerA(double incomerA) {
        this.incomerA = Math.max(0, incomerA);
    }

    public String rcdDescription() {
        return rcdDescription;
    }

    public void setRcdDescription(String rcdDescription) {
        this.rcdDescription = rcdDescription == null ? "" : rcdDescription;
    }

    public List<String> wayCircuitIds() {
        return Collections.unmodifiableList(wayCircuitIds);
    }

    public void setWayCircuit(int wayIndex0, String circuitId) {
        ensureWaySlots();
        if (wayIndex0 < 0 || wayIndex0 >= ways) {
            return;
        }
        wayCircuitIds.set(wayIndex0, circuitId);
    }

    public String circuitIdAtWay(int wayIndex0) {
        if (wayIndex0 < 0 || wayIndex0 >= wayCircuitIds.size()) {
            return null;
        }
        return wayCircuitIds.get(wayIndex0);
    }

    /** Assign circuits to sequential ways (1..n). */
    public void assignCircuitsInOrder(List<Circuit> circuits) {
        ensureWaySlots();
        for (int i = 0; i < ways; i++) {
            wayCircuitIds.set(i, null);
        }
        if (circuits == null) {
            return;
        }
        int way = 0;
        for (Circuit c : circuits) {
            if (way >= ways) {
                break;
            }
            wayCircuitIds.set(way, c.id());
            c.setWayNumber(way + 1);
            way++;
        }
    }

    public ConsumerUnit copy() {
        ConsumerUnit cu = new ConsumerUnit(id, name, ways, incomerA, rcdDescription);
        for (int i = 0; i < wayCircuitIds.size() && i < cu.ways; i++) {
            cu.wayCircuitIds.set(i, wayCircuitIds.get(i));
        }
        return cu;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConsumerUnit that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
