package com.ghana.gwire.service.cad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadCommandParserTest {

    @Test
    void parsesLineAndHelp() {
        assertEquals(CadCommandParser.Kind.LINE, CadCommandParser.parse("LINE").kind());
        assertEquals(CadCommandParser.Kind.LINE, CadCommandParser.parse("l").kind());
        assertEquals(CadCommandParser.Kind.HELP, CadCommandParser.parse("?").kind());
        assertEquals(CadCommandParser.Kind.CANCEL, CadCommandParser.parse("esc").kind());
    }

    @Test
    void parsesLengths() {
        assertEquals(CadCommandParser.Kind.LENGTH, CadCommandParser.parse("3500").kind());
        assertEquals(3500, CadCommandParser.parse("3500").valueMm(), 1e-9);
        assertEquals(3500, CadCommandParser.parse("3500mm").valueMm(), 1e-9);
        assertEquals(3500, CadCommandParser.parse("3.5m").valueMm(), 1e-9);
        assertEquals(3500, CadCommandParser.parse("3,5 m").valueMm(), 1e-9);
    }

    @Test
    void parsesOrthoOsnap() {
        assertEquals(CadCommandParser.Kind.ORTHO_ON, CadCommandParser.parse("ORTHO ON").kind());
        assertEquals(CadCommandParser.Kind.ORTHO_OFF, CadCommandParser.parse("ORTHO OFF").kind());
        assertEquals(CadCommandParser.Kind.OSNAP_ON, CadCommandParser.parse("OSNAP").kind());
        assertEquals(CadCommandParser.Kind.OSNAP_OFF, CadCommandParser.parse("OSNAP OFF").kind());
    }

    @Test
    void unknownCommand() {
        assertEquals(CadCommandParser.Kind.UNKNOWN, CadCommandParser.parse("FOOBAR").kind());
        assertTrue(CadCommandParser.parse("FOOBAR").message().contains("Unknown"));
    }
}
