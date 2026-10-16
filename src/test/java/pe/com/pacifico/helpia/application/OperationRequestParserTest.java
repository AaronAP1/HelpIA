package pe.com.pacifico.helpia.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.common.Json;
import pe.com.pacifico.helpia.domain.OperationType;

class OperationRequestParserTest {

    private final OperationRequestParser parser = new OperationRequestParser();

    @Test
    void given_usuarioValido_when_parse_then_normaliza() {
        ObjectNode body = Json.MAPPER.createObjectNode().put("usuario", " MJPerez ").put("canal", "teams");

        OperationRequest request = parser.parse(OperationType.DESBLOQUEO, body);

        assertEquals("mjperez", request.usuario());
        assertEquals("TEAMS", request.canal());
        assertNull(request.celular());
    }

    @Test
    void given_sinUsuario_when_parse_then_userRequired() {
        assertError(ErrorCatalog.USER_REQUIRED, OperationType.RESETEO, Json.MAPPER.createObjectNode());
    }

    @Test
    void given_usuarioConCaracteresInvalidos_when_parse_then_invalidUser() {
        assertError(ErrorCatalog.INVALID_USER, OperationType.RESETEO,
                Json.MAPPER.createObjectNode().put("usuario", "mj perez"));
        assertError(ErrorCatalog.INVALID_USER, OperationType.RESETEO,
                Json.MAPPER.createObjectNode().put("usuario", 123));
    }

    @Test
    void given_celularDe9DigitosConEspacios_when_parse_then_acepta() {
        ObjectNode body = Json.MAPPER.createObjectNode().put("usuario", "mjperez").put("celular", "987 654 321");

        assertEquals("987654321", parser.parse(OperationType.ACTUALIZACION_CELULAR, body).celular());
    }

    @Test
    void given_celularInvalido_when_parse_then_invalidPhone() {
        for (String celular : new String[] {"87654321", "887654321", "9876543210", "98765432a"}) {
            assertError(ErrorCatalog.INVALID_PHONE, OperationType.ACTUALIZACION_CELULAR,
                    Json.MAPPER.createObjectNode().put("usuario", "mjperez").put("celular", celular));
        }
        assertError(ErrorCatalog.INVALID_PHONE, OperationType.ACTUALIZACION_CELULAR,
                Json.MAPPER.createObjectNode().put("usuario", "mjperez"));
    }

    private void assertError(ErrorCatalog expected, OperationType type, ObjectNode body) {
        ApiException e = assertThrows(ApiException.class, () -> parser.parse(type, body));
        assertEquals(expected, e.error());
    }
}
