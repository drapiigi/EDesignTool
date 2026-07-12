package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiversityCalculatorTest {

    @Test
    void lightingFullBandUnchangedBelow1000W() {
        assertEquals(800.0, DiversityCalculator.lightingAfterDiversityW(800), 1e-9);
        assertEquals(1000.0, DiversityCalculator.lightingAfterDiversityW(1000), 1e-9);
    }

    @Test
    void lightingApplies50PercentAbove1000W() {
        // 1000 + 0.5 * 500 = 1250
        assertEquals(1250.0, DiversityCalculator.lightingAfterDiversityW(1500), 1e-9);
    }

    @Test
    void socketSingleOutletNoDiversity() {
        assertEquals(180.0, DiversityCalculator.socketAfterDiversityW(180, 1), 1e-9);
    }

    @Test
    void socketMultipleOutletsFactor0_4() {
        assertEquals(360.0, DiversityCalculator.socketAfterDiversityW(900, 3), 1e-9);
    }

    @Test
    void cookerSimplifiedBsDiversity() {
        // 7000 W @ 230 V → I = 30.435 A
        // diversified A = 10 + 0.3 * (30.435 - 10) = 16.130
        // W = 16.130 * 230 ≈ 3710
        double after = DiversityCalculator.cookerAfterDiversityW(7000, 230);
        double ratedA = 7000.0 / 230.0;
        double expectedA = 10.0 + 0.3 * (ratedA - 10.0);
        assertEquals(expectedA * 230.0, after, 0.1);
        assertTrue(after < 7000);
    }

    @Test
    void overallFactorWhenMoreThanThreeCircuits() {
        assertEquals(1.0, DiversityCalculator.overallInstallationFactor(3), 1e-9);
        assertEquals(0.9, DiversityCalculator.overallInstallationFactor(4), 1e-9);
    }

    @Test
    void applyToCircuitsSetsAfterDiversityFields() {
        List<CircuitLoad> circuits = new ArrayList<>();
        CircuitLoad lighting = new CircuitLoad("Lighting – Room", CircuitKind.LIGHTING);
        lighting.setConnectedLoadW(1500);
        lighting.addDeviceId("d1");
        circuits.add(lighting);

        CircuitLoad sockets = new CircuitLoad("Sockets – Room", CircuitKind.SOCKET);
        sockets.setConnectedLoadW(720);
        sockets.addDeviceId("s1");
        sockets.addDeviceId("s2");
        circuits.add(sockets);

        double total = DiversityCalculator.applyToCircuits(circuits, 230);
        assertEquals(1250.0, lighting.afterDiversityLoadW(), 1e-6);
        assertEquals(720 * 0.4, sockets.afterDiversityLoadW(), 1e-6);
        // 2 circuits → overall factor 1.0
        assertEquals(1250.0 + 288.0, total, 1e-6);
        assertTrue(lighting.diversityFactor() < 1.0);
    }

    @Test
    void afterDiversityByKindDelegates() {
        double light = DiversityCalculator.afterDiversityLoadW(CircuitKind.LIGHTING, 1500, 0, 230);
        assertEquals(1250.0, light, 1e-9);
        double sock = DiversityCalculator.afterDiversityLoadW(CircuitKind.SOCKET, 500, 2, 230);
        assertEquals(200.0, sock, 1e-9);
        double heater = DiversityCalculator.afterDiversityLoadW(CircuitKind.WATER_HEATER, 3000, 0, 230);
        assertEquals(3000.0, heater, 1e-9);
    }
}
