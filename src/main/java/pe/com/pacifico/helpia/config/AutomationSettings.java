package pe.com.pacifico.helpia.config;

import java.util.Map;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.domain.OperationType;

/** Configuración de Azure Automation (cuenta, grupo de Hybrid Workers y nombres de runbooks). */
public record AutomationSettings(
        String accountResourceId,
        String apiVersion,
        String hybridWorkerGroup,
        Map<OperationType, String> runbooks) {

    public static AutomationSettings load(Env env) {
        String accountId = env.get("AUTOMATION_ACCOUNT_RESOURCE_ID");
        String group = env.get("AUTOMATION_HYBRID_WORKER_GROUP");
        Map<OperationType, String> runbooks = Map.of(
                OperationType.DESBLOQUEO, env.get("AUTOMATION_RUNBOOK_DESBLOQUEO"),
                OperationType.RESETEO, env.get("AUTOMATION_RUNBOOK_RESETEO"),
                OperationType.ACTUALIZACION_CELULAR, env.get("AUTOMATION_RUNBOOK_CELULAR"));

        boolean incomplete = !accountId.startsWith("/subscriptions/")
                || group.isEmpty()
                || runbooks.values().stream().anyMatch(String::isEmpty);
        if (incomplete) {
            throw new ApiException(ErrorCatalog.CONFIGURATION_ERROR);
        }
        return new AutomationSettings(accountId, env.get("AUTOMATION_API_VERSION", "2023-11-01"), group, runbooks);
    }

    public String runbookFor(OperationType type) {
        return runbooks.get(type);
    }
}
