package pe.com.pacifico.helpia.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.domain.DirectoryUser;

/**
 * Migración de find_user() y _get_exact_user() de user_find_logic.py.
 * 1) Búsqueda directa por UPN. 2) Si no hay coincidencia exacta, filtro por UPN o mail.
 * Exige coincidencia exacta y devuelve 409 AMBIGUOUS_EMAIL si hay más de un usuario.
 */
public final class UserLookupService {

    private final UserDirectory directory;

    public UserLookupService(UserDirectory directory) {
        this.directory = directory;
    }

    public UserExistsResult findByEmail(String email, String correlationId) {
        Optional<DirectoryUser> direct = directory.getByUserPrincipalName(email, correlationId);
        if (direct.isPresent()) {
            String matchedBy = exactMatch(direct.get(), email);
            if (matchedBy != null) {
                return toResult(email, direct.get(), matchedBy);
            }
        }

        List<DirectoryUser> exact = new ArrayList<>();
        List<String> matchedBy = new ArrayList<>();
        for (DirectoryUser user : directory.searchByEmail(email, correlationId)) {
            String match = exactMatch(user, email);
            if (match != null) {
                exact.add(user);
                matchedBy.add(match);
            }
        }

        if (exact.isEmpty()) {
            return UserExistsResult.notFound(email);
        }
        if (exact.size() > 1) {
            throw new ApiException(ErrorCatalog.AMBIGUOUS_EMAIL);
        }
        return toResult(email, exact.get(0), matchedBy.get(0));
    }

    static String exactMatch(DirectoryUser user, String email) {
        if (normalize(user.userPrincipalName()).equals(email)) {
            return "userPrincipalName";
        }
        if (normalize(user.mail()).equals(email)) {
            return "mail";
        }
        return null;
    }

    private static UserExistsResult toResult(String email, DirectoryUser user, String matchedBy) {
        return new UserExistsResult(email, true, matchedBy, user.samAccountName(), user.onPremisesDomainName(),
                user.accountEnabled());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
