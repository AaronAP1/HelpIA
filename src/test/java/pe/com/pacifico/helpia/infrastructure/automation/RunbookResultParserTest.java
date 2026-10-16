package pe.com.pacifico.helpia.infrastructure.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.domain.RunbookJobSnapshot;

class RunbookResultParserTest {

    @Test
    void given_jobCompletadoConResultadoOk_when_parse_then_exitoso() {
        String output = "Conectando al dominio...\n{\"resultado\":\"OK\",\"codigo\":\"DESBLOQUEADO\",\"mensaje\":\"Cuenta desbloqueada\"}";

        RunbookJobSnapshot snapshot = RunbookResultParser.toSnapshot("Completed", output);

        assertTrue(snapshot.terminal());
        assertTrue(snapshot.exitoso());
        assertEquals("DESBLOQUEADO", snapshot.codigo());
    }

    @Test
    void given_jobCompletadoConError_when_parse_then_fallido() {
        RunbookJobSnapshot snapshot = RunbookResultParser.toSnapshot("Completed",
                "{\"resultado\":\"ERROR\",\"codigo\":\"SIN_PERMISOS\"}");

        assertTrue(snapshot.terminal());
        assertFalse(snapshot.exitoso());
        assertEquals("SIN_PERMISOS", snapshot.codigo());
    }

    @Test
    void given_jobCompletadoSinJson_when_parse_then_runbookSinResultado() {
        RunbookJobSnapshot snapshot = RunbookResultParser.toSnapshot("Completed", "listo");

        assertEquals("RUNBOOK_SIN_RESULTADO", snapshot.codigo());
        assertFalse(snapshot.exitoso());
    }

    @Test
    void given_jobFallido_when_parse_then_terminalFallido() {
        RunbookJobSnapshot snapshot = RunbookResultParser.toSnapshot("Failed", null);

        assertTrue(snapshot.terminal());
        assertEquals("RUNBOOK_FAILED", snapshot.codigo());
    }

    @Test
    void given_jobEnEjecucion_when_parse_then_noTerminal() {
        for (String status : new String[] {"New", "Activating", "Running", "Suspended"}) {
            assertFalse(RunbookResultParser.toSnapshot(status, null).terminal(), status);
        }
    }
}
