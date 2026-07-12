package com.ghana.gwire.service.calc;

import com.ghana.gwire.domain.calc.CircuitKind;
import com.ghana.gwire.domain.calc.CircuitLoad;
import com.ghana.gwire.domain.calc.Severity;
import com.ghana.gwire.domain.calc.ValidationIssue;
import com.ghana.gwire.domain.components.ComponentCategory;
import com.ghana.gwire.domain.components.ElectricalComponent;
import com.ghana.gwire.domain.components.PlacedDevice;
import com.ghana.gwire.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardsValidatorTest {

    @Test
    void noRcdWithSocketsYieldsWarningNoRcd() {
        Project project = new Project("RCD check");
        ElectricalComponent socket = ElectricalComponent.builder(
                "SOCK-13A-1G", "13A single socket", ComponentCategory.SOCKET, "socket_13a"
        ).currentRatingA(13.0).build();
        PlacedDevice d = new PlacedDevice("SOCK-13A-1G", "socket_13a", 1000, 1000);
        project.floorPlan().addDevice(d);

        Map<String, ElectricalComponent> cat = Map.of(socket.id(), socket);
        List<ValidationIssue> issues = StandardsValidator.validate(
                project, List.of(), 5.0, cat);

        assertTrue(
                issues.stream().anyMatch(i ->
                        i.severity() == Severity.WARNING && "NO_RCD".equals(i.code())),
                "expected NO_RCD warning, got: " + issues
        );
    }

    @Test
    void rcdPresentSuppressesNoRcd() {
        Project project = new Project("With RCCB");
        ElectricalComponent socket = ElectricalComponent.builder(
                "SOCK-13A-1G", "13A single socket", ComponentCategory.SOCKET, "socket_13a"
        ).build();
        ElectricalComponent rccb = ElectricalComponent.builder(
                "RCCB-40-30", "RCCB 40A 30mA", ComponentCategory.PROTECTION, "rccb_30ma"
        ).currentRatingA(40.0).build();
        project.floorPlan().addDevice(new PlacedDevice("SOCK-13A-1G", "socket_13a", 0, 0));
        project.floorPlan().addDevice(new PlacedDevice("RCCB-40-30", "rccb_30ma", 100, 100));

        Map<String, ElectricalComponent> cat = Map.of(
                socket.id(), socket,
                rccb.id(), rccb
        );
        List<ValidationIssue> issues = StandardsValidator.validate(project, List.of(), 5.0, cat);
        assertTrue(issues.stream().noneMatch(i -> "NO_RCD".equals(i.code())));
    }

    @Test
    void voltageDropExceededWarning() {
        Project project = new Project("Vd");
        CircuitLoad c = new CircuitLoad("Sockets – Hall", CircuitKind.SOCKET);
        c.setVoltageDropPercent(6.5);
        c.setDesignCurrentA(10);
        List<ValidationIssue> issues = StandardsValidator.validate(
                project, List.of(c), 10.0, Map.of());
        assertTrue(issues.stream().anyMatch(i ->
                "VD_EXCEEDED".equals(i.code()) && i.severity() == Severity.WARNING));
    }

    @Test
    void lightingOver16AIsError() {
        Project project = new Project("Light split");
        CircuitLoad c = new CircuitLoad("Lighting – Hall", CircuitKind.LIGHTING);
        c.setDesignCurrentA(18.0);
        c.setConnectedLoadW(4000);
        List<ValidationIssue> issues = StandardsValidator.validate(
                project, List.of(c), 18.0, Map.of());
        assertTrue(issues.stream().anyMatch(i ->
                "LIGHTING_SPLIT".equals(i.code()) && i.severity() == Severity.ERROR));
    }

    @Test
    void nextStandardBreaker() {
        assertEquals(6, StandardsValidator.nextStandardBreakerA(4), 1e-9);
        assertEquals(16, StandardsValidator.nextStandardBreakerA(12), 1e-9);
        assertEquals(32, StandardsValidator.nextStandardBreakerA(25), 1e-9);
        assertEquals(63, StandardsValidator.nextStandardBreakerA(50), 1e-9);
    }

    @Test
    void noEarthWarningWhenDevicesPresent() {
        Project project = new Project("Earth");
        ElectricalComponent light = ElectricalComponent.builder(
                "LIGHT-LED-12W", "LED 12W", ComponentCategory.LIGHTING, "light_led"
        ).powerW(12.0).build();
        project.floorPlan().addDevice(new PlacedDevice("LIGHT-LED-12W", "light_led", 0, 0));
        Map<String, ElectricalComponent> cat = new HashMap<>();
        cat.put(light.id(), light);
        List<ValidationIssue> issues = StandardsValidator.validate(project, List.of(), 0.1, cat);
        assertTrue(issues.stream().anyMatch(i -> "NO_EARTH".equals(i.code())));
    }
}
