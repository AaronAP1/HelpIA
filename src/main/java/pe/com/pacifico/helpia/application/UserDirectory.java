package pe.com.pacifico.helpia.application;

import java.util.List;
import java.util.Optional;
import pe.com.pacifico.helpia.domain.DirectoryUser;

/** Puerto hacia el directorio (Entra ID). Implementado por GraphUserDirectory. */
public interface UserDirectory {

    /** GET /users/{upn}. Vacío si Graph responde 404 (o 400 por un identificador que no acepta). */
    Optional<DirectoryUser> getByUserPrincipalName(String email, String correlationId);

    /** Filtro exacto por userPrincipalName o mail. Devuelve como máximo 2 resultados. */
    List<DirectoryUser> searchByEmail(String email, String correlationId);

    /** Filtro por usuario de red (onPremisesSamAccountName). Puede devolver una cuenta por dominio. */
    List<DirectoryUser> searchBySamAccountName(String samAccountName, String correlationId);
}
