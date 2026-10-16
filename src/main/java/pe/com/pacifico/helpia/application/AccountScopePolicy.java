package pe.com.pacifico.helpia.application;

import pe.com.pacifico.helpia.domain.DirectoryUser;

/**
 * Regla de alcance del acta (acuerdos 4 y 6): solo cuentas nominales de los dominios permitidos.
 * Las genéricas, de contacto, de servicio y otras no nominales no se procesan por autogestión.
 */
public interface AccountScopePolicy {

    boolean isInScope(DirectoryUser user);
}
