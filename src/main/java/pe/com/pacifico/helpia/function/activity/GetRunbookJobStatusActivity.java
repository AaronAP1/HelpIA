package pe.com.pacifico.helpia.function.activity;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import pe.com.pacifico.helpia.domain.RunbookJob;
import pe.com.pacifico.helpia.domain.RunbookJobSnapshot;
import pe.com.pacifico.helpia.function.Dependencies;
import pe.com.pacifico.helpia.infrastructure.automation.AutomationClient;
import pe.com.pacifico.helpia.infrastructure.automation.RunbookResultParser;

/** Paso 8 del diagrama: "resultado (async)". Consulta el estado del job y, si terminó, su salida. */
public class GetRunbookJobStatusActivity {

    public static final String NAME = "GetRunbookJobStatusActivity";

    @FunctionName(NAME)
    public RunbookJobSnapshot run(@DurableActivityTrigger(name = "job") RunbookJob job,
            final ExecutionContext context) {
        AutomationClient automation = Dependencies.get().automationClient();
        String status = automation.getJobStatus(job.jobName());
        String output = RunbookResultParser.isCompleted(status) ? automation.getJobOutput(job.jobName()) : null;

        RunbookJobSnapshot snapshot = RunbookResultParser.toSnapshot(status, output);
        if (snapshot.terminal()) {
            context.getLogger().info("Job terminado; estado=" + status + "; codigo=" + snapshot.codigo()
                    + "; correlation-id=" + job.correlationId());
        }
        return snapshot;
    }
}
