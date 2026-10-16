package pe.com.pacifico.helpia.infrastructure.automation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import pe.com.pacifico.helpia.domain.OperationType;

/**
 * Nombre determinista del job a partir del correlationId.
 * Si Durable reintenta la activity, se vuelve a pedir el MISMO job y Automation responde 409:
 * nunca se ejecuta dos veces la misma operación sobre el AD.
 */
public final class JobNames {

    private JobNames() {
    }

    public static String forOperation(String correlationId, OperationType type) {
        byte[] seed = ("helpia:" + correlationId + ":" + type.name()).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(seed).toString();
    }
}
