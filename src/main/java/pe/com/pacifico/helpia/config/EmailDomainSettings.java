package pe.com.pacifico.helpia.config;

import java.util.Set;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.common.ErrorCatalog;

/**
 * Dominios de correo permitidos.
 * Corrección respecto a Python: si la lista existe pero está vacía, Python aceptaba cualquier dominio.
 * Aquí falla cerrado (503 CONFIGURATION_ERROR).
 */
public record EmailDomainSettings(Set<String> allowedDomains) {

    public static EmailDomainSettings load(Env env) {
        Set<String> domains = env.getList("ALLOWED_EMAIL_DOMAINS", true);
        if (domains.isEmpty()) {
            throw new ApiException(ErrorCatalog.CONFIGURATION_ERROR);
        }
        return new EmailDomainSettings(Set.copyOf(domains));
    }
}
