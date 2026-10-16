package pe.com.pacifico.helpia.function.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.durabletask.OrchestrationMetadata;
import com.microsoft.durabletask.OrchestrationRuntimeStatus;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import java.time.Instant;
import java.util.Optional;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.CorrelationIdResolver;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.domain.OperationResult;
import pe.com.pacifico.helpia.domain.OperationStatus;
import pe.com.pacifico.helpia.domain.OperationType;
import pe.com.pacifico.helpia.domain.OrchestrationProgress;
import pe.com.pacifico.helpia.function.Dependencies;

/**
 * Endpoints de la Etapa 3 del diagrama (respuesta 200 o 202 + callback):
 * - POST /api/usuario/desbloquear
 * - POST /api/usuarios/resetear
 * - PUT  /api/usuarios/celular
 * Las rutas replican el diagrama tal cual (incluida la mezcla singular/plural); homologar antes de
 * congelar el contrato. Adicional al diagrama: GET /api/operaciones/{correlationId} como respaldo del callback.
 */
public class AccountOperationFunctions {

    @FunctionName("DesbloquearUsuario")
    public HttpResponseMessage desbloquear(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "usuario/desbloquear") HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {
        return OperationStarter.start(request, durableContext, context, OperationType.DESBLOQUEO);
    }

    @FunctionName("ResetearUsuario")
    public HttpResponseMessage resetear(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "usuarios/resetear") HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {
        return OperationStarter.start(request, durableContext, context, OperationType.RESETEO);
    }

    @FunctionName("ActualizarCelular")
    public HttpResponseMessage actualizarCelular(
            @HttpTrigger(name = "req", methods = {HttpMethod.PUT}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "usuarios/celular") HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {
        return OperationStarter.start(request, durableContext, context, OperationType.ACTUALIZACION_CELULAR);
    }

    @FunctionName("ConsultarOperacion")
    public HttpResponseMessage consultar(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "operaciones/{correlationId}") HttpRequestMessage<Optional<String>> request,
            @BindingName("correlationId") String operationId,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {

        String correlationId = CorrelationIdResolver.isValid(operationId)
                ? operationId.trim()
                : CorrelationIdResolver.resolve(request.getHeaders());
        return HttpSupport.handle(request, context, correlationId, () -> {
            Dependencies.get().jwtValidator().validateForOperations(request.getHeaders());
            if (!CorrelationIdResolver.isValid(operationId)) {
                throw new ApiException(ErrorCatalog.OPERATION_NOT_FOUND);
            }

            OrchestrationMetadata metadata = OperationStarter.findInstance(durableContext.getClient(), correlationId);
            if (metadata == null) {
                throw new ApiException(ErrorCatalog.OPERATION_NOT_FOUND);
            }
            if (metadata.getRuntimeStatus() == OrchestrationRuntimeStatus.COMPLETED) {
                return OperationResponses.completed(request, metadata.readOutputAs(OperationResult.class));
            }
            OrchestrationProgress progress = OperationStarter.readProgress(metadata);
            if (progress != null && progress.isReady()) {
                return OperationResponses.completed(request, progress.resultado());
            }
            if (metadata.getRuntimeStatus() == OrchestrationRuntimeStatus.FAILED
                    || metadata.getRuntimeStatus() == OrchestrationRuntimeStatus.TERMINATED) {
                OperationResult failed = new OperationResult(correlationId, null, OperationStatus.FALLIDO,
                        "ORQUESTACION_FALLIDA", "La operación no se pudo completar; derivar a Mesa de Ayuda.",
                        Instant.now(Dependencies.get().clock()).toString(), null);
                return OperationResponses.completed(request, failed);
            }
            return OperationResponses.pendingStatus(request, correlationId);
        });
    }
}
