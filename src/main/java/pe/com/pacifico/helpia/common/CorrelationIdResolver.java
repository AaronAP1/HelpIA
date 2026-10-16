package pe.com.pacifico.helpia.common;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resuelve el correlationId de la solicitud.
 * Orden: header Transaccion-Id (lineamientos del chapter) y luego x-correlation-id (contrato Python).
 * Si ninguno es válido se genera un UUID, igual que en Python.
 * El patrón es restrictivo porque el valor se escribe en logs y en headers de respuesta.
 */
public final class CorrelationIdResolver {

    public static final String HEADER_TRANSACCION_ID = "Transaccion-Id";
    public static final String HEADER_CORRELATION_ID = "x-correlation-id";

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    private CorrelationIdResolver() {
    }

    public static String resolve(Map<String, String> headers) {
        for (String header : new String[] {HEADER_TRANSACCION_ID, HEADER_CORRELATION_ID}) {
            String supplied = Headers.get(headers, header);
            if (isValid(supplied)) {
                return supplied.trim();
            }
        }
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value.trim()).matches();
    }
}
