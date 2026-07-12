package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.geometry.Vec2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableLengthEstimatorTest {

    @Test
    void defaultWhenNoDb() {
        assertEquals(15.0, CableLengthEstimator.estimateLengthM(
                List.of(new Vec2(0, 0)), List.of(), CircuitKind.SOCKET), 1e-9);
        assertEquals(12.0, CableLengthEstimator.estimateLengthM(
                List.of(new Vec2(0, 0)), List.of(), CircuitKind.LIGHTING), 1e-9);
    }

    @Test
    void manhattanAverageWithRoutingAndAllowance() {
        // Device at (3000, 4000), DB at (0,0) → man = 7000 mm = 7 m
        // length = 7 * 1.25 + 2 = 10.75 m
        double len = CableLengthEstimator.estimateLengthM(
                List.of(new Vec2(3000, 4000)),
                List.of(new Vec2(0, 0)),
                CircuitKind.SOCKET
        );
        assertEquals(10.75, len, 1e-9);
    }

    @Test
    void findsDbByCategoryAndSymbol() {
        ElectricalComponent db = ElectricalComponent.builder(
                "DB-8WAY", "8-way", ComponentCategory.DISTRIBUTION_BOARD, "db_8way"
        ).build();
        PlacedDevice d = new PlacedDevice("DB-8WAY", "db_8way", 100, 200);
        List<Vec2> positions = CableLengthEstimator.findDistributionBoardPositions(
                List.of(d), Map.of(db.id(), db));
        assertEquals(1, positions.size());
        assertEquals(100, positions.get(0).x(), 1e-9);
    }

    @Test
    void findsDbBySymbolPrefixWithoutCatalogue() {
        PlacedDevice d = new PlacedDevice("unknown", "db_4way", 50, 50);
        List<Vec2> positions = CableLengthEstimator.findDistributionBoardPositions(
                List.of(d), Map.of());
        assertEquals(1, positions.size());
        assertTrue(positions.get(0).x() == 50);
    }
}
