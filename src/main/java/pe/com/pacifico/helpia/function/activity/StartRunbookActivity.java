package pe.com.pacifico.helpia.function.activity;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import pe.com.pacifico.helpia.domain.OperationCommand;
import pe.com.pacifico.helpia.domain.RunbookJob;
import pe.com.pacifico.helpia.function.Dependencies;
import pe.com.pacifico.helpia.infrastructure.automation.JobNames;
import pe.com.pacifico.helpia.infrastructure.automation.RunbookParameters;

/** Paso 5 del diagrama: "dispara Runbook (async) · crea job (correlationId)". */
public class StartRunbookActivity {

    public static final String NAME = "StartRunbookActivity";

    @FunctionName(NAME)
    public RunbookJob run(@DurableActivityTrigger(name = "command") OperationCommand command,
            final ExecutionContext context) {
        Dependencies deps = Dependencies.get();
        String jobName = JobNames.forOperation(command.correlationId(), command.operacion());
        String runbook = deps.automationSettings().runbookFor(command.operacion());

        deps.automationClient().createJob(jobName, runbook, RunbookParameters.from(command));

        context.getLogger().info("Job de Automation creado; operacion=" + command.operacion()
                + "; job=" + jobName + "; correlation-id=" + command.correlationId());
        return new RunbookJob(jobName, command.correlationId(), command.operacion());
    }
}
