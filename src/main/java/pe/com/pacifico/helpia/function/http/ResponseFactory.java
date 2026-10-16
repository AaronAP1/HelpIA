package pe.com.pacifico.helpia.function.http;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;
import java.util.LinkedHashMap;
import java.util.Map;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Json;

/**
 * Migración de _json_response() de router.py: mismo formato de cuerpo (correlationId siempre presente),
 * mismos headers de seguridad y WWW-Authenticate en los 401.
 * Se agrega el header Transaccion-Id (lineamientos) además de x-correlation-id (contrato Python).
 */
public final class ResponseFactory {

    private ResponseFactory() {
    }

    public static HttpResponseMessage json(HttpRequestMessage<?> request, int status,
            Map<String, Object> payload, String correlationId) {
        return json(request, status, payload, correlationId, Map.of());
    }

    public static HttpResponseMessage json(HttpRequestMessage<?> request, int status,
            Map<String, Object> payload, String correlationId, Map<String, String> extraHeaders) {
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.put("correlationId", correlationId);

        HttpResponseMessage.Builder builder = request.createResponseBuilder(HttpStatusType.custom(status))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-correlation-id", correlationId)
                .header("Transaccion-Id", correlationId)
                .header("Cache-Control", "no-store, max-age=0")
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        if (status == 401) {
            builder.header("WWW-Authenticate", "Bearer");
        }
        extraHeaders.forEach(builder::header);
        return builder.body(Json.write(body)).build();
    }

    public static HttpResponseMessage error(HttpRequestMessage<?> request, ApiException e, String correlationId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", e.code());
        error.put("message", e.getMessage());
        return json(request, e.httpStatus(), Map.of("error", error), correlationId);
    }
}
