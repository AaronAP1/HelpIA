package pe.com.pacifico.helpia.function.activity;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import pe.com.pacifico.helpia.domain.OperationResult;
import pe.com.pacifico.helpia.function.Dependencies;

/** Paso 10 del diagrama: "CALLBACK POST /helpia/callback/{correlationId}" firmado con HMAC (secreto en Key Vault). */
public class SendCallbackActivity {

    public static final String NAME = "SendCallbackActivity";

    @FunctionName(NAME)
    public String run(@DurableActivityTrigger(name = "result") OperationResult result,
            final ExecutionContext context) {
        Dependencies.get().callbackClient().send(result);
        context.getLogger().info("Callback entregado; estado=" + result.estado()
                + "; correlation-id=" + result.correlationId());
        return "ENTREGADO";
    }
}
