package pe.com.pacifico.helpia.function.orchestration;

import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.TaskCanceledException;
import com.microsoft.durabletask.TaskFailedException;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;
import java.time.Duration;
import java.time.Instant;
import pe.com.pacifico.helpia.domain.DeliveryMode;
import pe.com.pacifico.helpia.domain.OperationCommand;
import pe.com.pacifico.helpia.domain.OperationResult;
import pe.com.pacifico.helpia.domain.OperationStatus;
import pe.com.pacifico.helpia.domain.OrchestrationProgress;
import pe.com.pacifico.helpia.domain.OrchestrationTimings;
import pe.com.pacifico.helpia.domain.RunbookJob;
import pe.com.pacifico.helpia.domain.RunbookJobSnapshot;
import pe.com.pacifico.helpia.function.activity.GetRunbookJobStatusActivity;
import pe.com.pacifico.helpia.function.activity.SendCallbackActivity;
import pe.com.pacifico.helpia.function.activity.StartRunbookActivity;

/**
 * Orquestador único para desbloqueo, reseteo y cambio de celular (pasos 5 a 10 del diagrama):
 * 1. Crea el job del runbook en Azure Automation (corre en el Hybrid Worker on-premise).
 * 2. Monitorea el job con timers durables (CreateTimer) hasta que termine o venza el plazo (wait-with-timeout).
 * 3. Publica el resultado en el custom status (el starter lo lee para responder 200).
 * 4. Si la respuesta fue asíncrona, envía el callback firmado con reintentos (RetryOptions).
 *
 * REGLAS DE DURABLE (no romper):
 * - Código determinista: nada de System.currentTimeMillis, UUID.randomUUID, I/O ni variables de entorno aquí.
 *   Usar ctx.getCurrentInstant() y llamar activities para todo lo externo.
 * - No capturar RuntimeException genérica: Durable usa excepciones internas para suspender la ejecución.
 * - No renombrar este orquestador ni las activities una vez desplegados (el historial guarda los nombres).
 *   Para cambios de lógica incompatibles, crear AccountOperationOrchestratorV2.
 */
public class AccountOperationOrchestrator {

    public static final String NAME = "AccountOperationOrchestrator";

    @FunctionName(NAME)
    public OperationResult run(@DurableOrchestrationTrigger(name = "ctx") TaskOrchestrationContext ctx) {
        OperationCommand command = ctx.getInput(OperationCommand.class);
        OrchestrationTimings timings = command.timings();
        ctx.setCustomStatus(OrchestrationProgress.running());

        OperationResult result = execute(ctx, command, timings);
        ctx.setCustomStatus(OrchestrationProgress.ready(result));

        if (DeliveryMode.SINCRONO.equals(waitDeliveryMode(ctx, timings))) {
            return result;
        }
        return sendCallback(ctx, result, timings);
    }

    private OperationResult execute(TaskOrchestrationContext ctx, OperationCommand command, OrchestrationTimings timings) {
        try {
            RunbookJob job = ctx.callActivity(StartRunbookActivity.NAME, command,
                    RetryOptions.automation(), RunbookJob.class).await();

            Instant deadline = ctx.getCurrentInstant().plusSeconds(timings.jobMaxWaitSeconds());
            Duration wait = Duration.ofSeconds(timings.jobPollInitialSeconds());
            Duration maxWait = Duration.ofSeconds(timings.jobPollMaxSeconds());

            while (true) {
                ctx.createTimer(wait).await();
                RunbookJobSnapshot snapshot = ctx.callActivity(GetRunbookJobStatusActivity.NAME, job,
                        RetryOptions.automation(), RunbookJobSnapshot.class).await();

                if (snapshot.terminal()) {
                    OperationStatus status = snapshot.exitoso() ? OperationStatus.EXITOSO : OperationStatus.FALLIDO;
                    return OperationResult.of(command, status, snapshot.codigo(), snapshot.mensaje(),
                            ctx.getCurrentInstant());
                }

                wait = PollingPolicy.next(wait, maxWait);
                if (!PollingPolicy.fitsBefore(ctx.getCurrentInstant(), wait, deadline)) {
                    return OperationResult.of(command, OperationStatus.TIMEOUT, "TIEMPO_AGOTADO",
                            "La operación no terminó a tiempo; derivar a Mesa de Ayuda.", ctx.getCurrentInstant());
                }
            }
        } catch (TaskFailedException e) {
            return OperationResult.of(command, OperationStatus.FALLIDO, "ERROR_EJECUCION",
                    "No fue posible completar la operación; derivar a Mesa de Ayuda.", ctx.getCurrentInstant());
        }
    }

    private String waitDeliveryMode(TaskOrchestrationContext ctx, OrchestrationTimings timings) {
        try {
            return ctx.waitForExternalEvent(DeliveryMode.EVENT_NAME,
                    Duration.ofSeconds(timings.deliveryModeWaitSeconds()), String.class).await();
        } catch (TaskCanceledException e) {
            // Sin noticias del starter: se asume asíncrono para no perder la respuesta.
            return DeliveryMode.ASINCRONO;
        }
    }

    private OperationResult sendCallback(TaskOrchestrationContext ctx, OperationResult result, OrchestrationTimings timings) {
        try {
            ctx.callActivity(SendCallbackActivity.NAME, result, RetryOptions.callback(timings), String.class).await();
            return result.withCallbackDelivered(true);
        } catch (TaskFailedException e) {
            // El resultado sigue disponible en GET /api/operaciones/{correlationId}.
            return result.withCallbackDelivered(false);
        }
    }
}
