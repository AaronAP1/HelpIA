package pe.com.pacifico.helpia.infrastructure.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import pe.com.pacifico.helpia.domain.OperationCommand;
import pe.com.pacifico.helpia.domain.OperationType;

/**
 * Parámetros que reciben los runbooks. Los nombres deben coincidir con los param() de PowerShell
 * (ver docs/CONTRATO-RUNBOOKS.md).
 */
public final class RunbookParameters {

    private RunbookParameters() {
    }

    public static Map<String, String> from(OperationCommand cmd) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("CorrelationId", cmd.correlationId());
        params.put("Operacion", cmd.operacion().name());
        params.put("Usuario", cmd.usuario());
        params.put("Dominios", String.join(",", cmd.dominios()));
        if (cmd.operacion() == OperationType.ACTUALIZACION_CELULAR) {
            params.put("Celular", cmd.celular());
        }
        return params;
    }
}
