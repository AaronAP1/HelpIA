package pe.com.pacifico.helpia.function.http;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import pe.com.pacifico.helpia.domain.OperationResult;
import pe.com.pacifico.helpia.domain.OperationStatus;
import pe.com.pacifico.helpia.domain.OperationType;

/** Cuerpos de respuesta de las operaciones asíncronas (200 con resultado / 202 pendiente). */
final class OperationResponses {

    static final int RETRY_AFTER_SECONDS = 10;

    private OperationResponses() {
    }

    static HttpResponseMessage completed(HttpRequestMessage<?> request, OperationResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operacion", result.operacion());
        body.put("estado", result.estado());
        body.put("codigo", result.codigo());
        body.put("mensaje", result.mensaje());
        body.put("finalizadoEn", result.finalizadoEn());
        if (result.callbackEntregado() != null) {
            body.put("callbackEntregado", result.callbackEntregado());
        }
        return ResponseFactory.json(request, 200, body, result.correlationId());
    }

    static HttpResponseMessage pending(HttpRequestMessage<?> request, OperationType type, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operacion", type);
        body.put("estado", OperationStatus.PENDIENTE);
        body.put("mensaje", "La operación está en proceso; el resultado se enviará por callback.");
        return ResponseFactory.json(request, 202, body, correlationId, Map.of(
                "Location", "/api/operaciones/" + correlationId,
                "Retry-After", String.valueOf(RETRY_AFTER_SECONDS)));
    }

    static HttpResponseMessage pendingStatus(HttpRequestMessage<?> request, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("estado", OperationStatus.PENDIENTE);
        return ResponseFactory.json(request, 200, body, correlationId,
                Map.of("Retry-After", String.valueOf(RETRY_AFTER_SECONDS)));
    }
}
