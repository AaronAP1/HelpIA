package pe.com.pacifico.helpia.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;

class UserLookupServiceTest {

    private final FakeUserDirectory directory = new FakeUserDirectory();
    private final UserLookupService service = new UserLookupService(directory);

    @Test
    void given_coincidenciaDirectaPorUpn_when_find_then_noUsaFiltro() {
        directory.direct = FakeUserDirectory.user("jperez@pacifico.com.pe", null, "jperez", "pacificoseguros.com", true);

        UserExistsResult result = service.findByEmail("jperez@pacifico.com.pe", "c1");

        assertTrue(result.exists());
        assertEquals("userPrincipalName", result.matchedBy());
        assertEquals("jperez", result.matricula());
        assertEquals(0, directory.searchByEmailCalls);
    }

    @Test
    void given_coincidenciaPorMail_when_find_then_matchedByMail() {
        directory.byEmail.add(FakeUserDirectory.user("x123@pacifico.com.pe", "jperez@pacifico.com.pe", "x123", "d", true));

        UserExistsResult result = service.findByEmail("jperez@pacifico.com.pe", "c1");

        assertEquals("mail", result.matchedBy());
    }

    @Test
    void given_dosCoincidencias_when_find_then_ambiguousEmail() {
        directory.byEmail.add(FakeUserDirectory.user("jperez@pacifico.com.pe", null, "a", "d", true));
        directory.byEmail.add(FakeUserDirectory.user("otro@pacifico.com.pe", "jperez@pacifico.com.pe", "b", "d", true));

        ApiException e = assertThrows(ApiException.class, () -> service.findByEmail("jperez@pacifico.com.pe", "c1"));
        assertEquals(ErrorCatalog.AMBIGUOUS_EMAIL, e.error());
    }

    @Test
    void given_sinCoincidencias_when_find_then_existsFalseConCamposNulos() {
        UserExistsResult result = service.findByEmail("nadie@pacifico.com.pe", "c1");

        Map<String, Object> body = result.toResponseBody();
        assertFalse(result.exists());
        assertEquals(false, body.get("emailMatches"));
        assertNull(body.get("matricula"));
        assertEquals("OK", body.get("status"));
    }

    @Test
    void given_resultado_when_toResponseBody_then_conservaOrdenDePython() {
        directory.direct = FakeUserDirectory.user("jperez@pacifico.com.pe", null, "jperez", "d", true);

        Map<String, Object> body = service.findByEmail("jperez@pacifico.com.pe", "c1").toResponseBody();

        assertEquals("[status, email, exists, emailMatches, matchedBy, matricula, dominioLocal, cuentaHabilitada]",
                body.keySet().toString());
    }
}
