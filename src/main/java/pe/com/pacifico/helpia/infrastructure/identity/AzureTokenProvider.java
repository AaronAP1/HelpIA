package pe.com.pacifico.helpia.infrastructure.identity;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import java.util.regex.Pattern;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.common.ErrorCatalog;

/**
 * Equivale a _build_graph_credential() y get_graph_access_token() de authentication.py.
 * En Azure: Managed Identity (user-assigned si AZURE_CLIENT_ID está definido).
 * En local: sesión de Azure CLI (az login) contra AZURE_TENANT_ID, o un token fijo si
 * LOCAL_FAKE_IDENTITY=true (solo para el simulador de Graph en Docker; nunca aplica en Azure).
 */
public final class AzureTokenProvider implements AccessTokenProvider {

    static final String SIMULATED_TOKEN = "token-local-simulado";
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    private final TokenCredential credential;
    private final String fixedToken;

    private AzureTokenProvider(TokenCredential credential, String fixedToken) {
        this.credential = credential;
        this.fixedToken = fixedToken;
    }

    public static AzureTokenProvider fromEnvironment(Env env) {
        if (env.isRunningInAzure()) {
            ManagedIdentityCredentialBuilder builder = new ManagedIdentityCredentialBuilder();
            String clientId = env.get("AZURE_CLIENT_ID");
            if (!clientId.isEmpty()) {
                builder.clientId(clientId);
            }
            return new AzureTokenProvider(builder.build(), null);
        }
        if (env.isTrue("LOCAL_FAKE_IDENTITY")) {
            return new AzureTokenProvider(null, SIMULATED_TOKEN);
        }
        String tenantId = env.get("AZURE_TENANT_ID").toLowerCase();
        if (!GUID.matcher(tenantId).matches()) {
            throw new ApiException(ErrorCatalog.GRAPH_CONFIGURATION_ERROR);
        }
        return new AzureTokenProvider(new AzureCliCredentialBuilder().tenantId(tenantId).build(), null);
    }

    /** Credencial para SDKs de Azure (Table Storage). Es null con LOCAL_FAKE_IDENTITY. */
    public TokenCredential credential() {
        return credential;
    }

    @Override
    public String getToken(String scope) {
        if (fixedToken != null) {
            return fixedToken;
        }
        try {
            AccessToken token = credential.getTokenSync(new TokenRequestContext().addScopes(scope));
            if (token == null || token.getToken() == null) {
                throw new TokenUnavailableException("Token vacío para " + scope, null);
            }
            return token.getToken();
        } catch (ClientAuthenticationException | IllegalArgumentException e) {
            // CredentialUnavailableException es subclase de ClientAuthenticationException.
            throw new TokenUnavailableException("No fue posible obtener token para " + scope, e);
        }
    }
}
