package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableSizerTest {

    @Test
    void voltageDropFormulaKnownMvAm() {
        // Vd = (mV/A/m * I * L) / 1000
        // 18 mV/A/m, 20 A, 25 m → 18*20*25/1000 = 9 V
        assertEquals(9.0, CableSizer.voltageDropV(18.0, 20.0, 25.0), 1e-9);
        assertEquals(100.0 * 9.0 / 230.0, CableSizer.voltageDropPercent(9.0, 230.0), 1e-9);
    }

    @Test
    void picksSmallestCsaMeetingCurrentAndVd() {
        List<ElectricalComponent> cables = List.of(
                cable("C1", 1.5, 16, 29.0, false),
                cable("C2", 2.5, 24, 18.0, false),
                cable("C4", 4.0, 32, 11.0, false)
        );
        // 10 A, 20 m, 230 V, max 5% → max Vd = 11.5 V
        // 1.5: 29*10*20/1000 = 5.8 V OK, rating 16 >= 10
        Optional<CableSizer.CableSelection> sel =
                CableSizer.size(10, 20, 230, 5.0, cables, CircuitKind.OTHER);
        assertTrue(sel.isPresent());
        assertEquals(1.5, sel.get().cable().crossSectionMm2(), 1e-9);
        assertEquals(5.8, sel.get().voltageDropV(), 1e-9);
    }

    @Test
    void prefersTwinEarthForLightingWhenSameCsa() {
        List<ElectricalComponent> cables = List.of(
                cable("CABLE-1.5-PVC", 1.5, 16, 29.0, false),
                cable("CABLE-1.5-TWIN", 1.5, 16, 29.0, true),
                cable("CABLE-2.5-PVC", 2.5, 24, 18.0, false)
        );
        Optional<CableSizer.CableSelection> sel =
                CableSizer.size(6, 15, 230, 5.0, cables, CircuitKind.LIGHTING);
        assertTrue(sel.isPresent());
        assertTrue(CableSizer.isTwinEarth(sel.get().cable()));
        assertEquals("CABLE-1.5-TWIN", sel.get().cable().id());
    }

    @Test
    void longRunMayForceLargerCsaForVd() {
        List<ElectricalComponent> cables = List.of(
                cable("C1", 1.5, 16, 29.0, false),
                cable("C2", 2.5, 24, 18.0, false),
                cable("C4", 4.0, 32, 11.0, false)
        );
        // 16 A, 40 m: 1.5 → 29*16*40/1000 = 18.56 V > 11.5 (5% of 230)
        // 2.5 → 18*16*40/1000 = 11.52 V slightly over
        // 4.0 → 11*16*40/1000 = 7.04 V OK
        Optional<CableSizer.CableSelection> sel =
                CableSizer.size(16, 40, 230, 5.0, cables, CircuitKind.SOCKET);
        assertTrue(sel.isPresent());
        assertEquals(4.0, sel.get().cable().crossSectionMm2(), 1e-9);
        assertTrue(sel.get().voltageDropPercent() <= 5.0 + 1e-6);
    }

    @Test
    void emptyCatalogueReturnsEmpty() {
        assertTrue(CableSizer.size(10, 10, 230, 5.0, List.of(), CircuitKind.SOCKET).isEmpty());
    }

    private static ElectricalComponent cable(
            String id,
            double mm2,
            double amp,
            double mvAm,
            boolean twin
    ) {
        String symbol = twin ? "cable_twin_" + mm2 : "cable_" + mm2;
        String name = twin ? mm2 + " mm² Twin & Earth" : mm2 + " mm² PVC";
        return ElectricalComponent.builder(id, name, ComponentCategory.CABLE, symbol)
                .standardSize(mm2 + "mm²")
                .unit("m")
                .currentRatingA(amp)
                .crossSectionMm2(mm2)
                .voltageDropMvPerAm(mvAm)
                .resistanceOhmPerKm(mvAm / 2) // placeholder
                .build();
    }
}
