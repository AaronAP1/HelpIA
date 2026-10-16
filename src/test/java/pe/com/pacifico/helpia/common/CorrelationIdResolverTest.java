package pe.com.pacifico.helpia.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CorrelationIdResolverTest {

    @Test
    void given_transaccionIdValido_when_resolve_then_loUsa() {
        assertEquals("abc-123", CorrelationIdResolver.resolve(Map.of("transaccion-id", "abc-123")));
    }

    @Test
    void given_soloXCorrelationId_when_resolve_then_loUsa() {
        assertEquals("abc.123", CorrelationIdResolver.resolve(Map.of("X-Correlation-Id", "abc.123")));
    }

    @Test
    void given_valorInvalido_when_resolve_then_generaUuid() {
        String id = CorrelationIdResolver.resolve(Map.of("x-correlation-id", "abc 123"));
        assertNotEquals("abc 123", id);
        assertTrue(id.matches("^[0-9a-f-]{36}$"));
    }

    @Test
    void given_valorDemasiadoLargo_when_resolve_then_generaUuid() {
        String id = CorrelationIdResolver.resolve(Map.of("x-correlation-id", "a".repeat(101)));
        assertEquals(36, id.length());
    }
}
