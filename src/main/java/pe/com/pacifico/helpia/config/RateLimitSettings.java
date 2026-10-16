package pe.com.pacifico.helpia.config;

import pe.com.pacifico.helpia.common.Env;

/** Límites de uso definidos en los flujos aprobados (acta Fase 3). */
public record RateLimitSettings(
        boolean enabled,
        String tableName,
        String tableEndpoint,
        String connectionString,
        int maxUnlocksPerDay,
        int maxResetsPerDay,
        int hoursBetweenPhoneChanges) {

    public static RateLimitSettings load(Env env) {
        return new RateLimitSettings(
                !"false".equalsIgnoreCase(env.get("RATE_LIMIT_ENABLED", "true")),
                env.get("RATE_LIMIT_TABLE_NAME", "HelpiaLimites"),
                env.get("RATE_LIMIT_TABLE_ENDPOINT"),
                env.get("RATE_LIMIT_CONNECTION_STRING"),
                env.getInt("MAX_DESBLOQUEOS_DIA", 3),
                env.getInt("MAX_RESETEOS_DIA", 3),
                env.getInt("HORAS_ENTRE_CAMBIOS_CELULAR", 24));
    }
}
