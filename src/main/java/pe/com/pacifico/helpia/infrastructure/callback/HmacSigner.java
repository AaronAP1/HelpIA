package pe.com.pacifico.helpia.infrastructure.callback;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Firma del callback: Base64(HMAC-SHA256(secreto, timestamp + "." + body)).
 * El bot (Agente HelpIA) debe recalcularla con el mismo secreto (guardado en Key Vault) y rechazar
 * firmas inválidas o timestamps con más de 5 minutos de antigüedad (anti-replay).
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public HmacSigner(String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el callback", e);
        }
    }

    /** Comparación en tiempo constante. Útil para pruebas y como referencia para el consumidor. */
    public boolean verify(String timestamp, String body, String signature) {
        byte[] expected = sign(timestamp, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature == null ? new byte[0] : signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
