package pe.com.pacifico.helpia.infrastructure.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.domain.OperationCommand;
import pe.com.pacifico.helpia.domain.OperationType;

class JobNamesTest {

    @Test
    void given_mismoCorrelationId_when_generar_then_mismoJob() {
        assertEquals(JobNames.forOperation("abc", OperationType.RESETEO), JobNames.forOperation("abc", OperationType.RESETEO));
    }

    @Test
    void given_distintaOperacion_when_generar_then_distintoJob() {
        assertNotEquals(JobNames.forOperation("abc", OperationType.RESETEO), JobNames.forOperation("abc", OperationType.DESBLOQUEO));
    }

    @Test
    void given_comandoCelular_when_parametros_then_incluyeCelular() {
        OperationCommand cmd = new OperationCommand("abc", OperationType.ACTUALIZACION_CELULAR, "mjperez",
                List.of("pacificoseguros.com"), "987654321", "TEAMS", "2026-10-14T15:00:00Z", null);

        Map<String, String> params = RunbookParameters.from(cmd);

        assertEquals("987654321", params.get("Celular"));
        assertEquals("pacificoseguros.com", params.get("Dominios"));
    }

    @Test
    void given_comandoDesbloqueo_when_parametros_then_noIncluyeCelular() {
        OperationCommand cmd = new OperationCommand("abc", OperationType.DESBLOQUEO, "mjperez",
                List.of("a.com", "b.local"), null, null, "2026-10-14T15:00:00Z", null);

        Map<String, String> params = RunbookParameters.from(cmd);

        assertEquals("a.com,b.local", params.get("Dominios"));
        assertEquals(false, params.containsKey("Celular"));
    }
}
