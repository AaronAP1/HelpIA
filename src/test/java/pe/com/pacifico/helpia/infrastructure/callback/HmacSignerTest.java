package pe.com.pacifico.helpia.infrastructure.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HmacSignerTest {

    private static final String SECRET = "secreto-de-prueba-de-al-menos-32-chars";
    private static final String BODY = "{\"correlationId\":\"abc\"}";

    /** Vector calculado de forma independiente (Python hmac + hashlib). Sirve al bot para validar su implementación. */
    @Test
    void given_vectorConocido_when_sign_then_coincide() {
        assertEquals("qDhd++D4P5mtjthz7KX8gp1209jtLdc0PoURXwEWNFU=", new HmacSigner(SECRET).sign("1760000000", BODY));
    }

    @Test
    void given_bodyAlterado_when_verify_then_rechaza() {
        HmacSigner signer = new HmacSigner(SECRET);
        String signature = signer.sign("1760000000", BODY);

        assertTrue(signer.verify("1760000000", BODY, signature));
        assertFalse(signer.verify("1760000000", BODY.replace("abc", "abd"), signature));
        assertFalse(signer.verify("1760000001", BODY, signature));
    }
}
