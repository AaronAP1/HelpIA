package pe.com.pacifico.helpia.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import pe.com.pacifico.helpia.application.UserDirectory;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.common.Json;
import pe.com.pacifico.helpia.domain.DirectoryUser;
import pe.com.pacifico.helpia.infrastructure.identity.AccessTokenProvider;
import pe.com.pacifico.helpia.infrastructure.identity.TokenUnavailableException;

/**
 * Cliente de Microsoft Graph. Migración de _graph_get() y _get_exact_user() de user_find_logic.py.
 *
 * Diferencias intencionales con Python:
 * - Presupuesto total de tiempo (por defecto 8 s) en lugar de reintentos que podían sumar más de un minuto.
 * - Retry-After se respeta con tope (2 s).
 * - En la búsqueda directa, un 400 se trata como "no encontrado" y se sigue con el filtro.
 * - Se agregan accountEnabled (campo cuentaHabilitada, aditivo) y la búsqueda por usuario de red (Etapa 3).
 */
public final class GraphUserDirectory implements UserDirectory {

    static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    static final String SELECT_FIELDS = "id,userPrincipalName,mail,onPremisesSamAccountName,"
            + "onPremisesDomainName,onPremisesSyncEnabled,accountEnabled";

    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final Logger LOG = Logger.getLogger(GraphUserDirectory.class.getName());

    private final HttpClient http;
    private final AccessTokenProvider tokens;
    private final String baseUrl;
    private final int maxAttempts;
    private final Duration requestTimeout;
    private final Duration totalBudget;
    private final Duration retryAfterCap;

    public GraphUserDirectory(HttpClient http, AccessTokenProvider tokens, String baseUrl) {
        this(http, tokens, baseUrl, 3, Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofSeconds(2));
    }

    GraphUserDirectory(HttpClient http, AccessTokenProvider tokens, String baseUrl, int maxAttempts,
            Duration requestTimeout, Duration totalBudget, Duration retryAfterCap) {
        this.http = http;
        this.tokens = tokens;
        this.baseUrl = baseUrl;
        this.maxAttempts = maxAttempts;
        this.requestTimeout = requestTimeout;
        this.totalBudget = totalBudget;
        this.retryAfterCap = retryAfterCap;
    }

    @Override
    public Optional<DirectoryUser> getByUserPrincipalName(String email, String correlationId) {
        String path = "/users/" + encode(email) + "?$select=" + encode(SELECT_FIELDS);
        JsonNode node = get(path, correlationId, false, Set.of(400, 404));
        return node == null ? Optional.empty() : Optional.of(toUser(node));
    }

    @Override
    public List<DirectoryUser> searchByEmail(String email, String correlationId) {
        String escaped = escapeOData(email);
        String filter = "userPrincipalName eq '" + escaped + "' or mail eq '" + escaped + "'";
        String path = "/users?$select=" + encode(SELECT_FIELDS)
                + "&$filter=" + encode(filter)
                + "&$count=true&$top=2";
        return toUsers(get(path, correlationId, true, Set.of()));
    }

    @Override
    public List<DirectoryUser> searchBySamAccountName(String samAccountName, String correlationId) {
        String filter = "onPremisesSamAccountName eq '" + escapeOData(samAccountName) + "'";
        String path = "/users?$select=" + encode(SELECT_FIELDS)
                + "&$filter=" + encode(filter)
                + "&$count=true&$top=5";
        return toUsers(get(path, correlationId, true, Set.of()));
    }

    private JsonNode get(String pathAndQuery, String correlationId, boolean eventualConsistency, Set<Integer> notFound) {
        String token = graphToken();
        long startNanos = System.nanoTime();
        int attempt = 0;

        while (true) {
            attempt++;
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("client-request-id", correlationId)
                    .header("return-client-request-id", "true")
                    .GET();
            if (eventualConsistency) {
                builder.header("ConsistencyLevel", "eventual");
            }

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                Duration wait = backoff(attempt);
                if (canRetry(attempt, startNanos, wait)) {
                    sleep(wait);
                    continue;
                }
                throw new ApiException(ErrorCatalog.GRAPH_TIMEOUT, e);
            } catch (IOException e) {
                Duration wait = backoff(attempt);
                if (canRetry(attempt, startNanos, wait)) {
                    sleep(wait);
                    continue;
                }
                throw new ApiException(ErrorCatalog.GRAPH_UNAVAILABLE, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCatalog.GRAPH_UNAVAILABLE, e);
            }

            int status = response.statusCode();
            if (notFound.contains(status)) {
                return null;
            }
            if (status == 200) {
                return parseObject(response.body());
            }
            if (RETRYABLE.contains(status)) {
                Duration wait = retryAfter(response).orElse(backoff(attempt));
                if (canRetry(attempt, startNanos, wait)) {
                    sleep(wait);
                    continue;
                }
            }

            LOG.severe(() -> "Graph status=" + status
                    + "; request-id=" + response.headers().firstValue("request-id").orElse("no-disponible")
                    + "; correlation-id=" + correlationId);
            throw new ApiException(mapStatus(status));
        }
    }

    static ErrorCatalog mapStatus(int status) {
        if (status == 401 || status == 403) {
            return ErrorCatalog.GRAPH_PERMISSION_DENIED;
        }
        if (status == 429) {
            return ErrorCatalog.GRAPH_THROTTLED;
        }
        if (status >= 500) {
            return ErrorCatalog.GRAPH_UNAVAILABLE;
        }
        return ErrorCatalog.GRAPH_ERROR;
    }

    private String graphToken() {
        try {
            return tokens.getToken(GRAPH_SCOPE);
        } catch (TokenUnavailableException e) {
            LOG.warning(() -> "No fue posible autenticarse en Graph: " + e.getClass().getSimpleName());
            throw new ApiException(ErrorCatalog.GRAPH_AUTHENTICATION_UNAVAILABLE, e);
        }
    }

    private boolean canRetry(int attempt, long startNanos, Duration wait) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return attempt < maxAttempts && elapsed.plus(wait).plus(requestTimeout).compareTo(totalBudget) <= 0;
    }

    private static Duration backoff(int attempt) {
        return Duration.ofMillis(400L * (1L << Math.max(0, attempt - 1)));
    }

    private Optional<Duration> retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                Duration d = Duration.ofSeconds(Long.parseLong(value.trim()));
                return Optional.of(d.compareTo(retryAfterCap) > 0 ? retryAfterCap : d);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCatalog.GRAPH_UNAVAILABLE, e);
        }
    }

    private static JsonNode parseObject(String body) {
        try {
            JsonNode node = Json.MAPPER.readTree(body);
            if (node == null || !node.isObject()) {
                throw new ApiException(ErrorCatalog.INVALID_GRAPH_RESPONSE);
            }
            return node;
        } catch (IOException e) {
            throw new ApiException(ErrorCatalog.INVALID_GRAPH_RESPONSE, e);
        }
    }

    private static List<DirectoryUser> toUsers(JsonNode document) {
        List<DirectoryUser> users = new ArrayList<>();
        JsonNode values = document.path("value");
        if (values.isArray()) {
            for (JsonNode node : values) {
                if (node.isObject()) {
                    users.add(toUser(node));
                }
            }
        }
        return users;
    }

    static DirectoryUser toUser(JsonNode node) {
        return new DirectoryUser(
                text(node, "id"),
                text(node, "userPrincipalName"),
                text(node, "mail"),
                text(node, "onPremisesSamAccountName"),
                text(node, "onPremisesDomainName"),
                bool(node, "onPremisesSyncEnabled"),
                bool(node, "accountEnabled"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    /** Escape OData de comillas simples, igual que Python. */
    static String escapeOData(String value) {
        return value.replace("'", "''");
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
