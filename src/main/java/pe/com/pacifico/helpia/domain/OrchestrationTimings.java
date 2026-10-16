package pe.com.pacifico.helpia.domain;

import pe.com.pacifico.helpia.common.Env;

/**
 * Tiempos de la orquestación. Viajan dentro del input para que el orquestador sea determinista
 * (no lee variables de entorno durante el replay).
 * La primera consulta del job es a los 2 s para que entre en la espera corta del diagrama (≈3 × 2 s → 200).
 */
public record OrchestrationTimings(
        int jobPollInitialSeconds,
        int jobPollMaxSeconds,
        int jobMaxWaitSeconds,
        int deliveryModeWaitSeconds,
        int callbackMaxAttempts,
        int callbackFirstRetrySeconds) {

    public static OrchestrationTimings load(Env env) {
        return new OrchestrationTimings(
                Math.max(1, env.getInt("JOB_POLL_INITIAL_SECONDS", 2)),
                Math.max(1, env.getInt("JOB_POLL_MAX_SECONDS", 30)),
                Math.max(30, env.getInt("JOB_MAX_WAIT_SECONDS", 600)),
                Math.max(5, env.getInt("DELIVERY_MODE_WAIT_SECONDS", 30)),
                Math.max(1, env.getInt("CALLBACK_MAX_ATTEMPTS", 5)),
                Math.max(1, env.getInt("CALLBACK_FIRST_RETRY_SECONDS", 5)));
    }
}
