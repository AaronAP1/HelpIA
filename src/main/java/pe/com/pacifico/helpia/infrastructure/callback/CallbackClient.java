package pe.com.pacifico.helpia.infrastructure.callback;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import pe.com.pacifico.helpia.common.Json;
import pe.com.pacifico.helpia.config.CallbackSettings;
import pe.com.pacifico.helpia.domain.OperationResult;

/**
 * Paso 10 del diagrama: POST {CALLBACK_BASE_URL}/helpia/callback/{correlationId}.
 * Headers: Transaccion-Id, X-HelpIA-Timestamp (epoch en segundos), X-HelpIA-Signature (v1=<base64>).
 * El consumidor debe ser idempotente por correlationId: ante reintentos puede llegar más de una vez.
 */
public final class CallbackClient {

    public static final String HEADER_TIMESTAMP = "X-HelpIA-Timestamp";
    public static final String HEADER_SIGNATURE = "X-HelpIA-Signature";

    private final HttpClient http;
    private final CallbackSettings settings;
    private final HmacSigner signer;
    private final Clock clock;

    public CallbackClient(HttpClient http, CallbackSettings settings, Clock clock) {
        this.http = http;
        this.settings = settings;
        this.signer = new HmacSigner(settings.hmacSecret());
        this.clock = clock;
    }

    public void send(OperationResult result) {
        String body = Json.write(payload(result));
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String url = settings.baseUrl() + "/helpia/callback/"
                + URLEncoder.encode(result.correlationId(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Transaccion-Id", result.correlationId())
                .header(HEADER_TIMESTAMP, timestamp)
                .header(HEADER_SIGNATURE, "v1=" + signer.sign(timestamp, body))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        int status;
        try {
            status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            throw new CallbackException("Error de red enviando el callback", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallbackException("Interrumpido", e);
        }
        if (status < 200 || status >= 300) {
            throw new CallbackException("El callback fue rechazado; status=" + status);
        }
    }

    static Map<String, Object> payload(OperationResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("correlationId", result.correlationId());
        body.put("operacion", result.operacion());
        body.put("estado", result.estado());
        body.put("codigo", result.codigo());
        body.put("mensaje", result.mensaje());
        body.put("finalizadoEn", result.finalizadoEn());
        return body;
    }
}
