package pe.com.pacifico.helpia.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** El custom status hace ida y vuelta por Jackson (DataConverter de Durable) entre el orquestador y el starter. */
class OrchestrationProgressTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OperationResult RESULT = new OperationResult("c-1", OperationType.DESBLOQUEO,
            OperationStatus.EXITOSO, "DESBLOQUEADO", "Tu cuenta fue desbloqueada.", "2026-10-16T05:00:00Z", null);

    @Test
    void given_resultadoListo_when_idaYVuelta_then_sigueListo() throws Exception {
        String json = MAPPER.writeValueAsString(OrchestrationProgress.ready(RESULT));

        assertFalse(json.contains("\"ready\""));
        assertTrue(MAPPER.readValue(json, OrchestrationProgress.class).isReady());
    }

    /** Instancias que ya estaban en curso con la versión anterior publicaron "ready" en el custom status. */
    @Test
    void given_jsonConCampoReady_when_leer_then_noFalla() throws Exception {
        String json = "{\"fase\":\"RESULTADO_LISTO\",\"resultado\":" + MAPPER.writeValueAsString(RESULT) + ",\"ready\":true}";

        assertTrue(MAPPER.readValue(json, OrchestrationProgress.class).isReady());
    }
}
