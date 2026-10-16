package pe.com.pacifico.helpia.domain;

/** Operaciones de la Etapa 3. Cada una se ejecuta con su propio runbook. */
public enum OperationType {
    DESBLOQUEO,
    RESETEO,
    ACTUALIZACION_CELULAR;

    /**
     * Según el acta: en Pacífico el desbloqueo aplica a todos los dominios del usuario,
     * mientras que el reseteo (y el cambio de celular) requiere una sola cuenta elegida.
     */
    public boolean requiresSingleAccount() {
        return this != DESBLOQUEO;
    }
}
