package pe.com.pacifico.helpia.function.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.OrchestrationMetadata;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import pe.com.pacifico.helpia.application.OperationRequest;
import pe.com.pacifico.helpia.auth.AuthenticatedCaller;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.CorrelationIdResolver;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.config.SyncWaitSettings;
import pe.com.pacifico.helpia.domain.DeliveryMode;
import pe.com.pacifico.helpia.domain.OperationCommand;
import pe.com.pacifico.helpia.domain.OperationType;
import pe.com.pacifico.helpia.domain.OrchestrationProgress;
import pe.com.pacifico.helpia.function.Dependencies;
import pe.com.pacifico.helpia.function.orchestration.AccountOperationOrchestrator;

/**
 * Starter HTTP de Durable. Implementa el comportamiento del diagrama:
 * "persiste en Task hub, crea job en Automation y hace polling corto (≈3 intentos x 2 s).
 *  Si el job completa → responde 200. Si no completa en 3 vueltas → 202 → async (PENDIENTE)".
 *
 * Coordinación con el orquestador (evita perder o duplicar la respuesta):
 * - Si el starter ve el resultado listo, responde 200 y envía el evento SINCRONO (no habrá callback).
 * - Si no, responde 202 y envía el evento ASINCRONO (el orquestador hará el callback).
 */
public final class OperationStarter {

    private OperationStarter() {
    }

    public static HttpResponseMessage start(HttpRequestMessage<Optional<String>> request,
            DurableClientContext durableContext, ExecutionContext context, OperationType type) {

        String correlationId = CorrelationIdResolver.resolve(request.getHeaders());
        return HttpSupport.handle(request, context, correlationId, () -> {
            Dependencies deps = Dependencies.get();
            AuthenticatedCaller caller = deps.jwtValidator().validateForOperations(request.getHeaders());
            JsonNode body = HttpSupport.readJsonObject(request);
            OperationRequest operation = deps.operationRequestParser().parse(type, body);
            List<String> dominios = deps.accountResolver().resolve(operation, caller, correlationId);

            DurableTaskClient client = durableContext.getClient();

            // Idempotencia: el mismo correlationId nunca lanza dos operaciones.
            OrchestrationMetadata existing = findInstance(client, correlationId);
            if (existing != null) {
                OrchestrationProgress progress = readProgress(existing);
                return progress != null && progress.isReady()
                        ? OperationResponses.completed(request, progress.resultado())
                        : OperationResponses.pending(request, type, correlationId);
            }

            deps.rateLimitService().acquire(type, operation.usuario());

            OperationCommand command = new OperationCommand(
                    correlationId, type, operation.usuario(), dominios, operation.celular(), operation.canal(),
                    Instant.now(deps.clock()).toString(), deps.orchestrationTimings());
            try {
                client.scheduleNewOrchestrationInstance(AccountOperationOrchestrator.NAME, command, correlationId);
            } catch (RuntimeException e) {
                throw new ApiException(ErrorCatalog.ORCHESTRATION_UNAVAILABLE, e);
            }
            context.getLogger().info("Operación iniciada; operacion=" + type + "; correlation-id=" + correlationId);

            return waitShortly(request, client, type, correlationId, deps.syncWaitSettings(), context);
        });
    }

    private static HttpResponseMessage waitShortly(HttpRequestMessage<?> request, DurableTaskClient client,
            OperationType type, String correlationId, SyncWaitSettings wait, ExecutionContext context) {
        for (int poll = 0; poll < wait.polls(); poll++) {
            sleep(wait.intervalMillis());
            OrchestrationProgress progress = readProgress(findInstance(client, correlationId));
            if (progress != null && progress.isReady()) {
                notifyDeliveryMode(client, correlationId, DeliveryMode.SINCRONO, context);
                return OperationResponses.completed(request, progress.resultado());
            }
        }
        notifyDeliveryMode(client, correlationId, DeliveryMode.ASINCRONO, context);
        return OperationResponses.pending(request, type, correlationId);
    }

    static OrchestrationMetadata findInstance(DurableTaskClient client, String instanceId) {
        OrchestrationMetadata metadata = client.getInstanceMetadata(instanceId, true);
        return metadata != null && metadata.isInstanceFound() ? metadata : null;
    }

    /** readCustomStatusAs devuelve null si la orquestación todavía no publicó estado. */
    static OrchestrationProgress readProgress(OrchestrationMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return metadata.readCustomStatusAs(OrchestrationProgress.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Si el evento no llega, el orquestador asume ASINCRONO y hace el callback:
     * en el peor caso el bot recibe el resultado dos veces (200 y callback), nunca cero.
     */
    private static void notifyDeliveryMode(DurableTaskClient client, String instanceId, String mode,
            ExecutionContext context) {
        try {
            client.raiseEvent(instanceId, DeliveryMode.EVENT_NAME, mode);
        } catch (RuntimeException e) {
            context.getLogger().warning("No se pudo notificar el modo de entrega; correlation-id=" + instanceId);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
