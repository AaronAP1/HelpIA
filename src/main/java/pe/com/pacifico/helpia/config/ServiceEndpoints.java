package pe.com.pacifico.helpia.config;

import pe.com.pacifico.helpia.common.Env;

/**
 * URLs base de Microsoft Graph y de Azure Resource Manager (Automation). Solo fuera de Azure se pueden
 * reemplazar con GRAPH_BASE_URL y ARM_BASE_URL (simuladores de Docker). En Azure siempre se usan las URLs
 * reales, aunque los settings existan.
 */
public record ServiceEndpoints(String graphBaseUrl, String armBaseUrl) {

    public static final String GRAPH_DEFAULT = "https://graph.microsoft.com/v1.0";
    public static final String ARM_DEFAULT = "https://management.azure.com";

    public static ServiceEndpoints load(Env env) {
        if (env.isRunningInAzure()) {
            return new ServiceEndpoints(GRAPH_DEFAULT, ARM_DEFAULT);
        }
        return new ServiceEndpoints(
                env.get("GRAPH_BASE_URL", GRAPH_DEFAULT).replaceAll("/+$", ""),
                env.get("ARM_BASE_URL", ARM_DEFAULT).replaceAll("/+$", ""));
    }
}
