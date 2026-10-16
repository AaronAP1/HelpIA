package pe.com.pacifico.helpia.function.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.common.Headers;
import pe.com.pacifico.helpia.common.Json;

/** Manejo común de errores y lectura del body. Migra _known_error_response, _internal_error_response y _read_email_from_request. */
public final class HttpSupport {

    static final int MAX_REQUEST_BODY_BYTES = 2_048;

    private HttpSupport() {
    }

    @FunctionalInterface
    public interface Handler {
        HttpResponseMessage handle();
    }

    public static HttpResponseMessage handle(HttpRequestMessage<?> request, ExecutionContext context,
            String correlationId, Handler handler) {
        try {
            return handler.handle();
        } catch (ApiException e) {
            context.getLogger().warning("Solicitud rechazada; code=" + e.code() + "; correlation-id=" + correlationId);
            return ResponseFactory.error(request, e, correlationId);
        } catch (RuntimeException e) {
            context.getLogger().log(Level.SEVERE, "Error no controlado; correlation-id=" + correlationId, e);
            return ResponseFactory.error(request, new ApiException(ErrorCatalog.INTERNAL_ERROR), correlationId);
        }
    }

    /** Mismo orden que Python: tamaño → Content-Type → JSON válido → objeto. */
    public static JsonNode readJsonObject(HttpRequestMessage<Optional<String>> request) {
        String body = request.getBody() == null ? "" : request.getBody().orElse("");
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BODY_BYTES) {
            throw new ApiException(ErrorCatalog.REQUEST_TOO_LARGE);
        }

        String contentType = Optional.ofNullable(Headers.get(request.getHeaders(), "Content-Type")).orElse("");
        String mediaType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (!"application/json".equals(mediaType)) {
            throw new ApiException(ErrorCatalog.UNSUPPORTED_MEDIA_TYPE);
        }

        JsonNode node;
        try {
            node = Json.MAPPER.readTree(body);
        } catch (IOException e) {
            throw new ApiException(ErrorCatalog.INVALID_JSON, e);
        }
        if (node == null || !node.isObject()) {
            throw new ApiException(ErrorCatalog.INVALID_JSON);
        }
        return node;
    }
}
