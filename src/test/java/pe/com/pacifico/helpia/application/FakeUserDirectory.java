package pe.com.pacifico.helpia.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import pe.com.pacifico.helpia.domain.DirectoryUser;

/** Directorio en memoria para pruebas. */
class FakeUserDirectory implements UserDirectory {

    DirectoryUser direct;
    final List<DirectoryUser> byEmail = new ArrayList<>();
    final List<DirectoryUser> bySam = new ArrayList<>();
    int searchByEmailCalls;

    @Override
    public Optional<DirectoryUser> getByUserPrincipalName(String email, String correlationId) {
        return Optional.ofNullable(direct);
    }

    @Override
    public List<DirectoryUser> searchByEmail(String email, String correlationId) {
        searchByEmailCalls++;
        return byEmail;
    }

    @Override
    public List<DirectoryUser> searchBySamAccountName(String samAccountName, String correlationId) {
        return bySam;
    }

    static DirectoryUser user(String upn, String mail, String sam, String domain, boolean enabled) {
        return new DirectoryUser("id-" + sam, upn, mail, sam, domain, true, enabled);
    }
}
