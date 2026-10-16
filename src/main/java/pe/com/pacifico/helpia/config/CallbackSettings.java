package pe.com.pacifico.helpia.config;

import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.common.ErrorCatalog;

/**
 * Callback hacia el Agente HelpIA (bot).
 * En Azure, CALLBACK_HMAC_SECRET debe ser una referencia de Key Vault:
 * {@code @Microsoft.KeyVault(SecretUri=https://<key-vault>.vault.azure.net/secrets/...)}.
 * HTTP sin TLS solo se acepta fuera de Azure con APP_ENV=local (simulador del bot en Docker).
 */
public record CallbackSettings(String baseUrl, String hmacSecret) {

    public static CallbackSettings load(Env env) {
        String baseUrl = env.get("CALLBACK_BASE_URL");
        String secret = env.get("CALLBACK_HMAC_SECRET");
        boolean local = !env.isRunningInAzure() && "local".equalsIgnoreCase(env.get("APP_ENV"));
        if (!baseUrl.startsWith("https://") && !(baseUrl.startsWith("http://") && local)) {
            throw new ApiException(ErrorCatalog.CONFIGURATION_ERROR);
        }
        if (secret.length() < 32) {
            throw new ApiException(ErrorCatalog.CONFIGURATION_ERROR);
        }
        return new CallbackSettings(baseUrl.replaceAll("/+$", ""), secret);
    }
}
