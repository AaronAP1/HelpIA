package pe.com.pacifico.helpia.auth;

import java.util.Set;

/**
 * Quién llama a la API, según el JWT validado.
 * - Token de usuario (canal Teams): trae scopes delegados (scp) y el UPN del colaborador.
 * - Token de aplicación (canal WhatsApp): trae roles y no identifica al colaborador.
 */
public record AuthenticatedCaller(
        String clientId,
        String subject,
        String userPrincipalName,
        Set<String> scopes,
        Set<String> roles) {

    public boolean isUserToken() {
        return !scopes.isEmpty();
    }

    static AuthenticatedCaller localBypass() {
        return new AuthenticatedCaller("local", "local-bypass", null, Set.of(), Set.of("local"));
    }
}
