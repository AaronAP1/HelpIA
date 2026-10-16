package pe.com.pacifico.helpia.infrastructure.identity;

/** Obtiene tokens de Entra ID para la identidad de la Function. */
public interface AccessTokenProvider {

    /** @throws TokenUnavailableException si no se pudo obtener el token. */
    String getToken(String scope);
}
