package pe.com.pacifico.helpia.application;

import java.util.List;
import java.util.Locale;
import pe.com.pacifico.helpia.auth.AuthenticatedCaller;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.domain.DirectoryUser;

/**
 * Valida la cuenta antes de lanzar una operación, en el orden de los flujos aprobados:
 * existencia → selección de dominio → alcance (nominal) → habilitada → pertenece a quien llama.
 * Devuelve los dominios on-premise sobre los que debe actuar el runbook.
 */
public final class AccountResolver {

    private final UserDirectory directory;
    private final AccountScopePolicy scopePolicy;

    public AccountResolver(UserDirectory directory, AccountScopePolicy scopePolicy) {
        this.directory = directory;
        this.scopePolicy = scopePolicy;
    }

    public List<String> resolve(OperationRequest request, AuthenticatedCaller caller, String correlationId) {
        List<DirectoryUser> accounts = directory.searchBySamAccountName(request.usuario(), correlationId);

        if (request.dominio() != null) {
            accounts = accounts.stream().filter(a -> belongsToDomain(a, request.dominio())).toList();
        }
        if (accounts.isEmpty()) {
            throw new ApiException(ErrorCatalog.USER_NOT_FOUND);
        }
        // Pacífico tiene dos dominios: el reseteo y el cambio de celular requieren elegir la cuenta.
        if (request.type().requiresSingleAccount() && accounts.size() > 1) {
            throw new ApiException(ErrorCatalog.DOMAIN_REQUIRED);
        }
        if (accounts.stream().anyMatch(a -> !scopePolicy.isInScope(a))) {
            throw new ApiException(ErrorCatalog.ACCOUNT_OUT_OF_SCOPE);
        }
        if (accounts.stream().anyMatch(a -> Boolean.FALSE.equals(a.accountEnabled()))) {
            throw new ApiException(ErrorCatalog.ACCOUNT_DISABLED);
        }
        // Canal Teams (token de usuario): solo puede operar sobre su propia cuenta.
        if (caller.isUserToken()) {
            String callerUpn = caller.userPrincipalName();
            boolean owns = callerUpn != null && accounts.stream()
                    .anyMatch(a -> callerUpn.equalsIgnoreCase(a.userPrincipalName()));
            if (!owns) {
                throw new ApiException(ErrorCatalog.USER_MISMATCH);
            }
        }

        return accounts.stream()
                .map(DirectoryUser::onPremisesDomainName)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .toList();
    }

    static boolean belongsToDomain(DirectoryUser account, String domain) {
        String onPrem = account.onPremisesDomainName();
        if (onPrem == null) {
            return false;
        }
        String normalized = onPrem.toLowerCase(Locale.ROOT);
        return normalized.equals(domain) || normalized.startsWith(domain + ".");
    }
}
