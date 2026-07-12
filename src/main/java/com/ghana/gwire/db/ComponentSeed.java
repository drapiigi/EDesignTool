package com.ghana.gwire.db;

import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghana domestic wiring starter catalogue (placeholder GHS costs, editable later).
 *
 * <p><b>Cable electrical parameters</b> (current rating, resistance, mV/A/m voltage-drop
 * factor) are typical/illustrative values aligned with common BS/IEC practice for
 * PVC copper singles. They are <em>not</em> a substitute for manufacturer data or
 * full L.I. 2008 / installation-method tables — refine in Phase 4 calculation engine.
 */
public final class ComponentSeed {

    private ComponentSeed() {
    }

    public static List<ElectricalComponent> starterCatalogue() {
        List<ElectricalComponent> list = new ArrayList<>();
        addCables(list);
        addMcbs(list);
        addDistributionBoards(list);
        addSwitches(list);
        addSockets(list);
        addLighting(list);
        addProtection(list);
        addEarthing(list);
        addConduitAndJunction(list);
        addIsolatorsAndOther(list);
        return list;
    }

    // --- Cables: PVC Cu singles (typical BS/IEC sizing factors, Phase 4 refine) ---

    private static void addCables(List<ElectricalComponent> list) {
        // voltage_drop_mv_per_am: approx single-phase mV/A/m for Cu PVC
        // resistance_ohm_per_km: approx conductor resistance at 20 °C
        // current_rating_a: typical clipped-direct order of magnitude
        cable(list, "CABLE-1.5-PVC", "1.5 mm² PVC Cu cable", "1.5mm²",
                1.5, 16.0, 12.1, 29.0, 4.50);
        cable(list, "CABLE-2.5-PVC", "2.5 mm² PVC Cu cable", "2.5mm²",
                2.5, 24.0, 7.41, 18.0, 6.80);
        cable(list, "CABLE-4-PVC", "4 mm² PVC Cu cable", "4mm²",
                4.0, 32.0, 4.61, 11.0, 11.50);
        cable(list, "CABLE-6-PVC", "6 mm² PVC Cu cable", "6mm²",
                6.0, 41.0, 3.08, 7.3, 17.00);
        cable(list, "CABLE-10-PVC", "10 mm² PVC Cu cable", "10mm²",
                10.0, 57.0, 1.83, 4.4, 28.00);
        cable(list, "CABLE-16-PVC", "16 mm² PVC Cu cable", "16mm²",
                16.0, 76.0, 1.15, 2.8, 45.00);
        // Twin & earth style for lighting/power runs (same CSA as singles, different symbol)
        list.add(ElectricalComponent.builder("CABLE-1.5-TWIN", "1.5 mm² Twin & Earth", ComponentCategory.CABLE, "cable_twin_1_5")
                .description("Flat twin & earth PVC Cu for lighting circuits")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("1.5mm² T&E")
                .unit("m")
                .unitCostGhs(5.20)
                .currentRatingA(16.0)
                .voltageRatingV(450.0)
                .crossSectionMm2(1.5)
                .resistanceOhmPerKm(12.1)
                .voltageDropMvPerAm(29.0)
                .notes("Typical/illustrative BS/IEC practice for sizing; refine Phase 4")
                .build());
        list.add(ElectricalComponent.builder("CABLE-2.5-TWIN", "2.5 mm² Twin & Earth", ComponentCategory.CABLE, "cable_twin_2_5")
                .description("Flat twin & earth PVC Cu for ring/radial socket circuits")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("2.5mm² T&E")
                .unit("m")
                .unitCostGhs(7.80)
                .currentRatingA(24.0)
                .voltageRatingV(450.0)
                .crossSectionMm2(2.5)
                .resistanceOhmPerKm(7.41)
                .voltageDropMvPerAm(18.0)
                .notes("Typical/illustrative BS/IEC practice for sizing; refine Phase 4")
                .build());
    }

    private static void cable(
            List<ElectricalComponent> list,
            String id,
            String name,
            String size,
            double mm2,
            double amp,
            double rOhmKm,
            double mvAm,
            double cost
    ) {
        list.add(ElectricalComponent.builder(id, name, ComponentCategory.CABLE, symbolForCable(mm2))
                .description("PVC insulated copper single-core cable")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize(size)
                .unit("m")
                .unitCostGhs(cost)
                .currentRatingA(amp)
                .voltageRatingV(450.0)
                .crossSectionMm2(mm2)
                .resistanceOhmPerKm(rOhmKm)
                .voltageDropMvPerAm(mvAm)
                .notes("Typical/illustrative BS/IEC practice for sizing, to be refined Phase 4")
                .build());
    }

    private static String symbolForCable(double mm2) {
        if (mm2 <= 1.5) return "cable_1_5";
        if (mm2 <= 2.5) return "cable_2_5";
        if (mm2 <= 4) return "cable_4";
        if (mm2 <= 6) return "cable_6";
        if (mm2 <= 10) return "cable_10";
        return "cable_16";
    }

    // --- MCBs ---

    private static void addMcbs(List<ElectricalComponent> list) {
        int[] ratings = {6, 10, 16, 20, 32, 40, 63};
        for (int a : ratings) {
            list.add(mcb(a, 1));
            // 3P for larger final circuits / distribution; still include smaller for completeness
            if (a >= 16) {
                list.add(mcb(a, 3));
            }
        }
        // 6A and 10A 3P less common but useful for three-phase lighting/control
        list.add(mcb(6, 3));
        list.add(mcb(10, 3));
    }

    private static ElectricalComponent mcb(int amp, int poles) {
        String id = "MCB-%dA-%dP".formatted(amp, poles);
        String symbol = poles == 1 ? "mcb_1p" : "mcb_3p";
        return ElectricalComponent.builder(id, "%dA MCB %dP".formatted(amp, poles), ComponentCategory.CIRCUIT_BREAKER, symbol)
                .description("Miniature circuit breaker, Type B/C domestic")
                .ghanaReference("L.I. 2008 — overcurrent protection")
                .standardSize(amp + "A")
                .unit("pcs")
                .unitCostGhs(poles == 1 ? 35.0 + amp * 0.4 : 95.0 + amp * 0.8)
                .currentRatingA((double) amp)
                .voltageRatingV(poles == 1 ? 230.0 : 400.0)
                .poles(poles)
                .build();
    }

    // --- DBs / consumer units ---

    private static void addDistributionBoards(List<ElectricalComponent> list) {
        db(list, "DB-4WAY", "4-way consumer unit", "4-way", "db_4way", 280);
        db(list, "DB-6WAY", "6-way consumer unit", "6-way", "db_6way", 380);
        db(list, "DB-8WAY", "8-way consumer unit", "8-way", "db_8way", 480);
        db(list, "DB-12WAY", "12-way consumer unit", "12-way", "db_12way", 650);
        list.add(ElectricalComponent.builder("DB-SPLIT-8", "8-way split-load consumer unit", ComponentCategory.DISTRIBUTION_BOARD, "db_split_8")
                .description("Split-load board with dual RCCB sections")
                .ghanaReference("Energy Commission — consumer unit practice")
                .standardSize("8-way split")
                .unit("pcs")
                .unitCostGhs(720)
                .voltageRatingV(230.0)
                .build());
    }

    private static void db(List<ElectricalComponent> list, String id, String name, String size, String symbol, double cost) {
        list.add(ElectricalComponent.builder(id, name, ComponentCategory.DISTRIBUTION_BOARD, symbol)
                .description("Domestic consumer unit / distribution board")
                .ghanaReference("Energy Commission — consumer unit practice")
                .standardSize(size)
                .unit("pcs")
                .unitCostGhs(cost)
                .voltageRatingV(230.0)
                .build());
    }

    // --- Switches ---

    private static void addSwitches(List<ElectricalComponent> list) {
        sw(list, "SW-1G", "1-gang 1-way switch", "1-gang", "switch_1g", 18);
        sw(list, "SW-2G", "2-gang 1-way switch", "2-gang", "switch_2g", 28);
        sw(list, "SW-3G", "3-gang 1-way switch", "3-gang", "switch_3g", 38);
        sw(list, "SW-2W-1G", "1-gang 2-way switch", "1-gang 2-way", "switch_2way", 22);
        sw(list, "SW-DIMMER", "Dimmer switch", "dimmer", "switch_dimmer", 85);
        sw(list, "SW-INTER", "Intermediate switch", "intermediate", "switch_intermediate", 45);
        list.add(ElectricalComponent.builder("SW-20A-DP", "20A double-pole switch", ComponentCategory.SWITCH, "switch_20a_dp")
                .description("20A DP switch for water heater / cooker spur")
                .ghanaReference("L.I. 2008 — fixed appliances")
                .standardSize("20A DP")
                .unit("pcs")
                .unitCostGhs(55)
                .currentRatingA(20.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
        list.add(ElectricalComponent.builder("SW-45A-DP", "45A double-pole cooker switch", ComponentCategory.SWITCH, "switch_45a_dp")
                .description("Cooker control unit / 45A DP switch")
                .ghanaReference("L.I. 2008 — fixed appliances")
                .standardSize("45A DP")
                .unit("pcs")
                .unitCostGhs(120)
                .currentRatingA(45.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
    }

    private static void sw(List<ElectricalComponent> list, String id, String name, String size, String symbol, double cost) {
        list.add(ElectricalComponent.builder(id, name, ComponentCategory.SWITCH, symbol)
                .description("Wall-mounted lighting switch")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize(size)
                .unit("pcs")
                .unitCostGhs(cost)
                .currentRatingA(10.0)
                .voltageRatingV(230.0)
                .build());
    }

    // --- Sockets ---

    private static void addSockets(List<ElectricalComponent> list) {
        list.add(ElectricalComponent.builder("SOCK-13A-1G", "13A single socket (BS 1363)", ComponentCategory.SOCKET, "socket_13a")
                .description("BS 1363 switched socket outlet, single")
                .ghanaReference("BS 1363")
                .standardSize("13A 1-gang")
                .unit("pcs")
                .unitCostGhs(32)
                .currentRatingA(13.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("SOCK-13A-2G", "13A twin socket (BS 1363)", ComponentCategory.SOCKET, "socket_13a_2g")
                .description("BS 1363 switched twin socket outlet")
                .ghanaReference("BS 1363")
                .standardSize("13A 2-gang")
                .unit("pcs")
                .unitCostGhs(48)
                .currentRatingA(13.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("SOCK-15A", "15A round-pin socket", ComponentCategory.SOCKET, "socket_15a")
                .description("15A socket common on older Ghana installations")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("15A")
                .unit("pcs")
                .unitCostGhs(40)
                .currentRatingA(15.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("SOCK-5A", "5A round-pin socket", ComponentCategory.SOCKET, "socket_5a")
                .description("5A socket for lighting / low-power loads")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("5A")
                .unit("pcs")
                .unitCostGhs(25)
                .currentRatingA(5.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("SOCK-IND-16A", "16A industrial socket (IEC 60309)", ComponentCategory.SOCKET, "socket_industrial_16a")
                .description("Industrial blue connector socket 16A 230V")
                .ghanaReference("IEC 60309")
                .standardSize("16A industrial")
                .unit("pcs")
                .unitCostGhs(95)
                .currentRatingA(16.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("SOCK-IND-32A", "32A industrial socket (IEC 60309)", ComponentCategory.SOCKET, "socket_industrial_32a")
                .description("Industrial connector socket 32A")
                .ghanaReference("IEC 60309")
                .standardSize("32A industrial")
                .unit("pcs")
                .unitCostGhs(145)
                .currentRatingA(32.0)
                .voltageRatingV(400.0)
                .build());
    }

    // --- Lighting ---

    private static void addLighting(List<ElectricalComponent> list) {
        list.add(ElectricalComponent.builder("LIGHT-LED-9W", "LED bulb 9W", ComponentCategory.LIGHTING, "light_led")
                .description("E27 LED lamp approx 800 lm")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("9W LED")
                .unit("pcs")
                .unitCostGhs(18)
                .powerW(9.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("LIGHT-LED-12W", "LED bulb 12W", ComponentCategory.LIGHTING, "light_led")
                .description("E27 LED lamp approx 1000 lm")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("12W LED")
                .unit("pcs")
                .unitCostGhs(22)
                .powerW(12.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("LIGHT-FLUOR-18W", "Fluorescent batten 18W", ComponentCategory.LIGHTING, "light_fluorescent")
                .description("T8 fluorescent fitting (legacy stock)")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("18W fluoro")
                .unit("pcs")
                .unitCostGhs(55)
                .powerW(18.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("LIGHT-FLUOR-36W", "Fluorescent batten 36W", ComponentCategory.LIGHTING, "light_fluorescent")
                .description("T8 fluorescent fitting 4 ft")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("36W fluoro")
                .unit("pcs")
                .unitCostGhs(75)
                .powerW(36.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("LIGHT-ROSE", "Ceiling rose", ComponentCategory.LIGHTING, "light_ceiling_rose")
                .description("Ceiling rose for pendant/flexible cord")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("standard")
                .unit("pcs")
                .unitCostGhs(12)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("LIGHT-BULKHEAD", "Bulkhead luminaire", ComponentCategory.LIGHTING, "light_bulkhead")
                .description("Weatherproof bulkhead for external/utility areas")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("IP65")
                .unit("pcs")
                .unitCostGhs(85)
                .powerW(15.0)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("LIGHT-PANEL-24W", "LED panel 24W", ComponentCategory.LIGHTING, "light_panel")
                .description("Recessed/surface LED panel")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("24W panel")
                .unit("pcs")
                .unitCostGhs(120)
                .powerW(24.0)
                .voltageRatingV(230.0)
                .build());
    }

    // --- Protection ---

    private static void addProtection(List<ElectricalComponent> list) {
        list.add(ElectricalComponent.builder("RCCB-40-30", "RCCB 40A 30mA", ComponentCategory.PROTECTION, "rccb_30ma")
                .description("Residual current circuit breaker 30mA sensitivity")
                .ghanaReference("L.I. 2008 — residual current protection")
                .standardSize("40A 30mA")
                .unit("pcs")
                .unitCostGhs(180)
                .currentRatingA(40.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
        list.add(ElectricalComponent.builder("RCCB-63-30", "RCCB 63A 30mA", ComponentCategory.PROTECTION, "rccb_30ma")
                .description("Residual current circuit breaker 30mA, 63A")
                .ghanaReference("L.I. 2008 — residual current protection")
                .standardSize("63A 30mA")
                .unit("pcs")
                .unitCostGhs(220)
                .currentRatingA(63.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
        list.add(ElectricalComponent.builder("RCCB-63-100", "RCCB 63A 100mA", ComponentCategory.PROTECTION, "rccb_100ma")
                .description("Time-delayed / fire-protection RCCB 100mA")
                .ghanaReference("L.I. 2008 — residual current protection")
                .standardSize("63A 100mA")
                .unit("pcs")
                .unitCostGhs(240)
                .currentRatingA(63.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
        list.add(ElectricalComponent.builder("RCBO-32-30", "RCBO 32A 30mA 1P+N", ComponentCategory.PROTECTION, "rcbo_30ma")
                .description("Combined MCB+RCD for final circuit")
                .ghanaReference("L.I. 2008 — residual current protection")
                .standardSize("32A 30mA")
                .unit("pcs")
                .unitCostGhs(195)
                .currentRatingA(32.0)
                .voltageRatingV(230.0)
                .poles(1)
                .build());
        list.add(ElectricalComponent.builder("RCBO-20-30", "RCBO 20A 30mA 1P+N", ComponentCategory.PROTECTION, "rcbo_30ma")
                .description("Combined MCB+RCD for lighting/radial")
                .ghanaReference("L.I. 2008 — residual current protection")
                .standardSize("20A 30mA")
                .unit("pcs")
                .unitCostGhs(175)
                .currentRatingA(20.0)
                .voltageRatingV(230.0)
                .poles(1)
                .build());
        list.add(ElectricalComponent.builder("SPD-T2", "Surge protection device Type 2", ComponentCategory.PROTECTION, "spd_t2")
                .description("SPD for consumer unit incoming")
                .ghanaReference("Energy Commission — consumer unit practice")
                .standardSize("Type 2")
                .unit("pcs")
                .unitCostGhs(320)
                .voltageRatingV(230.0)
                .build());
    }

    // --- Earthing ---

    private static void addEarthing(List<ElectricalComponent> list) {
        list.add(ElectricalComponent.builder("EARTH-ROD-16", "Copper earth rod 16 mm", ComponentCategory.EARTHING, "earth_rod")
                .description("Driven copper-bonded earth electrode")
                .ghanaReference("L.I. 2008 — earthing")
                .standardSize("16mm × 1.2m")
                .unit("pcs")
                .unitCostGhs(95)
                .build());
        list.add(ElectricalComponent.builder("EARTH-CLAMP", "Earth rod clamp", ComponentCategory.EARTHING, "earth_clamp")
                .description("Clamp for rod to earthing conductor")
                .ghanaReference("L.I. 2008 — earthing")
                .standardSize("standard")
                .unit("pcs")
                .unitCostGhs(25)
                .build());
        list.add(ElectricalComponent.builder("EARTH-TAPE-25", "Copper earth tape 25×3 mm", ComponentCategory.EARTHING, "earth_tape")
                .description("Bare copper tape for equipotential bonding")
                .ghanaReference("L.I. 2008 — earthing")
                .standardSize("25×3 mm")
                .unit("m")
                .unitCostGhs(45)
                .crossSectionMm2(75.0)
                .build());
        list.add(ElectricalComponent.builder("EARTH-COND-6", "6 mm² CPC / earth conductor", ComponentCategory.EARTHING, "earth_conductor")
                .description("Green/yellow PVC Cu protective conductor")
                .ghanaReference("L.I. 2008 — earthing")
                .standardSize("6mm²")
                .unit("m")
                .unitCostGhs(8.50)
                .crossSectionMm2(6.0)
                .build());
        list.add(ElectricalComponent.builder("EARTH-COND-10", "10 mm² main earth conductor", ComponentCategory.EARTHING, "earth_conductor")
                .description("Main earthing conductor PVC Cu")
                .ghanaReference("L.I. 2008 — earthing")
                .standardSize("10mm²")
                .unit("m")
                .unitCostGhs(14.00)
                .crossSectionMm2(10.0)
                .build());
        list.add(ElectricalComponent.builder("EARTH-BOND-BAR", "Equipotential bonding bar", ComponentCategory.EARTHING, "earth_bond_bar")
                .description("Bonding bar for MET / water/gas bonds")
                .ghanaReference("L.I. 2008 — earthing")
                .standardSize("standard")
                .unit("pcs")
                .unitCostGhs(65)
                .build());
    }

    // --- Conduit & junction ---

    private static void addConduitAndJunction(List<ElectricalComponent> list) {
        list.add(ElectricalComponent.builder("COND-PVC-20", "PVC conduit 20 mm", ComponentCategory.CONDUIT, "conduit_pvc")
                .description("Heavy-gauge PVC conduit")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("20mm")
                .unit("m")
                .unitCostGhs(3.50)
                .build());
        list.add(ElectricalComponent.builder("COND-PVC-25", "PVC conduit 25 mm", ComponentCategory.CONDUIT, "conduit_pvc")
                .description("Heavy-gauge PVC conduit")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("25mm")
                .unit("m")
                .unitCostGhs(4.80)
                .build());
        list.add(ElectricalComponent.builder("COND-GI-20", "GI conduit 20 mm", ComponentCategory.CONDUIT, "conduit_gi")
                .description("Galvanised steel conduit")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("20mm")
                .unit("m")
                .unitCostGhs(12.00)
                .build());
        list.add(ElectricalComponent.builder("COND-GI-25", "GI conduit 25 mm", ComponentCategory.CONDUIT, "conduit_gi")
                .description("Galvanised steel conduit")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("25mm")
                .unit("m")
                .unitCostGhs(15.50)
                .build());
        list.add(ElectricalComponent.builder("JB-1WAY", "Junction box 1-way", ComponentCategory.JUNCTION, "junction_box")
                .description("PVC junction / joint box")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("1-way")
                .unit("pcs")
                .unitCostGhs(8)
                .build());
        list.add(ElectricalComponent.builder("JB-4WAY", "Junction box 4-way", ComponentCategory.JUNCTION, "junction_box")
                .description("PVC junction box multi-way")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("4-way")
                .unit("pcs")
                .unitCostGhs(15)
                .build());
        list.add(ElectricalComponent.builder("JB-DEEP", "Deep junction box", ComponentCategory.JUNCTION, "junction_box_deep")
                .description("Deep box for looping")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("deep")
                .unit("pcs")
                .unitCostGhs(18)
                .build());
    }

    // --- Isolators & other ---

    private static void addIsolatorsAndOther(List<ElectricalComponent> list) {
        list.add(ElectricalComponent.builder("ISO-32A-2P", "32A isolator 2P", ComponentCategory.ISOLATOR, "isolator_2p")
                .description("Local isolator for AC / plant")
                .ghanaReference("L.I. 2008 — isolation")
                .standardSize("32A 2P")
                .unit("pcs")
                .unitCostGhs(85)
                .currentRatingA(32.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
        list.add(ElectricalComponent.builder("ISO-63A-3P", "63A isolator 3P", ComponentCategory.ISOLATOR, "isolator_3p")
                .description("Three-phase isolator")
                .ghanaReference("L.I. 2008 — isolation")
                .standardSize("63A 3P")
                .unit("pcs")
                .unitCostGhs(210)
                .currentRatingA(63.0)
                .voltageRatingV(400.0)
                .poles(3)
                .build());
        list.add(ElectricalComponent.builder("CHG-63A", "Changeover switch 63A", ComponentCategory.OTHER, "changeover_63a")
                .description("Manual changeover for generator supply")
                .ghanaReference("Energy Commission — consumer unit practice")
                .standardSize("63A")
                .unit("pcs")
                .unitCostGhs(450)
                .currentRatingA(63.0)
                .voltageRatingV(230.0)
                .poles(2)
                .build());
        list.add(ElectricalComponent.builder("CHG-100A-3P", "Changeover switch 100A 3P", ComponentCategory.OTHER, "changeover_100a")
                .description("Three-phase generator changeover")
                .ghanaReference("Energy Commission — consumer unit practice")
                .standardSize("100A 3P")
                .unit("pcs")
                .unitCostGhs(980)
                .currentRatingA(100.0)
                .voltageRatingV(400.0)
                .poles(3)
                .build());
        list.add(ElectricalComponent.builder("METER-SINGLE", "Single-phase energy meter", ComponentCategory.OTHER, "meter_1p")
                .description("kWh meter / prepaid meter enclosure space")
                .ghanaReference("Energy Commission — consumer unit practice")
                .standardSize("1P")
                .unit("pcs")
                .unitCostGhs(350)
                .voltageRatingV(230.0)
                .build());
        list.add(ElectricalComponent.builder("TRUNK-25", "PVC trunking 25×16 mm", ComponentCategory.OTHER, "trunking_pvc")
                .description("Mini trunking for surface wiring")
                .ghanaReference("L.I. 2008 — general wiring")
                .standardSize("25×16")
                .unit("m")
                .unitCostGhs(6.50)
                .build());
    }
}
