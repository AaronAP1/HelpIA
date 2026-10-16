package pe.com.pacifico.helpia.domain;

import java.util.List;

/**
 * Input de la orquestación. Se persiste en el historial del Task hub:
 * NO debe contener contraseñas ni secretos. El celular (solo en ACTUALIZACION_CELULAR) es dato personal:
 * aplicar política de purga del historial (ver docs/DESPLIEGUE.md).
 */
public record OperationCommand(
        String correlationId,
        OperationType operacion,
        String usuario,
        List<String> dominios,
        String celular,
        String canal,
        String solicitadoEn,
        OrchestrationTimings timings) {
}
