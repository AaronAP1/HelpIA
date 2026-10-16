package pe.com.pacifico.helpia.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;

class EmailValidatorTest {

    private final EmailValidator validator = new EmailValidator(Set.of("pacifico.com.pe", "epps.com.pe"));
    private final JsonNodeFactory json = JsonNodeFactory.instance;

    @Test
    void given_emailConEspaciosYMayusculas_when_validate_then_normaliza() {
        assertEquals("jperez@pacifico.com.pe", validator.validate(json.textNode("  JPerez@Pacifico.com.pe ")));
    }

    @Test
    void given_emailAusente_when_validate_then_emailRequired() {
        assertError(ErrorCatalog.EMAIL_REQUIRED, null);
        assertError(ErrorCatalog.EMAIL_REQUIRED, json.nullNode());
        assertError(ErrorCatalog.EMAIL_REQUIRED, json.textNode("   "));
    }

    /** En Python estos casos devolvían 500 INTERNAL_ERROR. */
    @Test
    void given_emailNoTexto_when_validate_then_invalidEmail() {
        assertError(ErrorCatalog.INVALID_EMAIL, json.numberNode(123));
        assertError(ErrorCatalog.INVALID_EMAIL, json.arrayNode().add("a@pacifico.com.pe"));
        assertError(ErrorCatalog.INVALID_EMAIL, json.booleanNode(false));
    }

    @Test
    void given_formatoInvalido_when_validate_then_invalidEmail() {
        assertError(ErrorCatalog.INVALID_EMAIL, json.textNode("sin-arroba.pacifico.com.pe"));
        assertError(ErrorCatalog.INVALID_EMAIL, json.textNode("a@pacifico"));
        assertError(ErrorCatalog.INVALID_EMAIL, json.textNode("a".repeat(250) + "@pacifico.com.pe"));
    }

    @Test
    void given_dominioNoPermitidoOSubdominio_when_validate_then_domainNotAllowed() {
        assertError(ErrorCatalog.EMAIL_DOMAIN_NOT_ALLOWED, json.textNode("a@gmail.com"));
        assertError(ErrorCatalog.EMAIL_DOMAIN_NOT_ALLOWED, json.textNode("a@x.pacifico.com.pe"));
    }

    @Test
    void given_dominioEpps_when_validate_then_acepta() {
        assertEquals("a@epps.com.pe", validator.validate(json.textNode("a@epps.com.pe")));
    }

    private void assertError(ErrorCatalog expected, JsonNode input) {
        ApiException e = assertThrows(ApiException.class, () -> validator.validate(input));
        assertEquals(expected, e.error());
    }
}
