package pe.com.pacifico.helpia.domain;

/** Usuario leído de Entra ID (Microsoft Graph). Solo los campos necesarios. */
public record DirectoryUser(
        String id,
        String userPrincipalName,
        String mail,
        String samAccountName,
        String onPremisesDomainName,
        Boolean onPremisesSyncEnabled,
        Boolean accountEnabled) {
}
