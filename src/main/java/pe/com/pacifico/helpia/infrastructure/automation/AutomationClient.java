package pe.com.pacifico.helpia.infrastructure.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import pe.com.pacifico.helpia.common.Json;
import pe.com.pacifico.helpia.config.AutomationSettings;
import pe.com.pacifico.helpia.infrastructure.identity.AccessTokenProvider;
import pe.com.pacifico.helpia.infrastructure.identity.TokenUnavailableException;

/**
 * Cliente REST de Azure Automation (Azure Resource Manager), autenticado con la Managed Identity.
 * Rol requerido sobre la cuenta de Automation: "Automation Job Operator" (o equivalente).
 * El job corre en el grupo de Hybrid Workers on-premise (runOn).
 */
public final class AutomationClient {

    static final String ARM_SCOPE = "https://management.azure.com/.default";

    private final HttpClient http;
    private final AccessTokenProvider tokens;
    private final AutomationSettings settings;
    private final String baseUrl;

    public AutomationClient(HttpClient http, AccessTokenProvider tokens, AutomationSettings settings, String baseUrl) {
        this.http = http;
        this.tokens = tokens;
        this.settings = settings;
        this.baseUrl = baseUrl;
    }

    /** Crea el job. Es idempotente: si ya existe un job con ese nombre (reintento), no falla. */
    public void createJob(String jobName, String runbookName, Map<String, String> parameters) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        ObjectNode properties = body.putObject("properties");
        properties.putObject("runbook").put("name", runbookName);
        ObjectNode params = properties.putObject("parameters");
        parameters.forEach(params::put);
        properties.put("runOn", settings.hybridWorkerGroup());

        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(jobUrl(jobName, "")))
                .PUT(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .header("Content-Type", "application/json"));

        int status = response.statusCode();
        if (status == 200 || status == 201 || status == 409) {
            return;
        }
        throw new AutomationException("Automation rechazó la creación del job; status=" + status);
    }

    /** Devuelve properties.status del job (New, Activating, Running, Completed, Failed, Stopped, ...). */
    public String getJobStatus(String jobName) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(jobUrl(jobName, ""))).GET());
        if (response.statusCode() != 200) {
            throw new AutomationException("No se pudo consultar el job; status=" + response.statusCode());
        }
        try {
            JsonNode node = Json.MAPPER.readTree(response.body());
            String status = node.path("properties").path("status").asText("");
            if (status.isEmpty()) {
                throw new AutomationException("Respuesta de Automation sin estado");
            }
            return status;
        } catch (IOException e) {
            throw new AutomationException("Respuesta de Automation no válida", e);
        }
    }

    /** Devuelve la salida (stream Output) del job como texto. */
    public String getJobOutput(String jobName) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(jobUrl(jobName, "/output"))).GET());
        if (response.statusCode() != 200) {
            throw new AutomationException("No se pudo leer la salida del job; status=" + response.statusCode());
        }
        return response.body();
    }

    private String jobUrl(String jobName, String suffix) {
        return baseUrl + settings.accountResourceId() + "/jobs/" + jobName + suffix
                + "?api-version=" + settings.apiVersion();
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        String token;
        try {
            token = tokens.getToken(ARM_SCOPE);
        } catch (TokenUnavailableException e) {
            throw new AutomationException("Sin token para Azure Resource Manager", e);
        }
        HttpRequest request = builder
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AutomationException("Error de red con Azure Automation", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AutomationException("Interrumpido", e);
        }
    }
}
