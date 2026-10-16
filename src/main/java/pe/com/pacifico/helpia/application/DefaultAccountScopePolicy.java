package pe.com.pacifico.helpia.application;

import java.util.Locale;
import java.util.Set;
import pe.com.pacifico.helpia.domain.DirectoryUser;

/**
 * Implementación inicial del alcance:
 * - la cuenta debe venir del AD on-premise (onPremisesSyncEnabled = true), y
 * - su UPN debe pertenecer a un dominio permitido (ALLOWED_EMAIL_DOMAINS).
 *
 * PENDIENTE (acuerdo 6 del acta, responsable IAM): definir el atributo confiable que distingue
 * una cuenta nominal (employeeType, extensionAttribute, OU, grupo...). Cuando se defina,
 * agregarlo al $select de Graph y evaluarlo aquí.
 */
public final class DefaultAccountScopePolicy implements AccountScopePolicy {

    private final Set<String> allowedDomains;

    public DefaultAccountScopePolicy(Set<String> allowedDomains) {
        this.allowedDomains = Set.copyOf(allowedDomains);
    }

    @Override
    public boolean isInScope(DirectoryUser user) {
        if (!Boolean.TRUE.equals(user.onPremisesSyncEnabled())) {
            return false;
        }
        String upn = user.userPrincipalName();
        if (upn == null || !upn.contains("@")) {
            return false;
        }
        return allowedDomains.contains(EmailValidator.domainOf(upn.toLowerCase(Locale.ROOT)));
    }
}
