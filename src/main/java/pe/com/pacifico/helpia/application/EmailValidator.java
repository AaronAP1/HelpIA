package pe.com.pacifico.helpia.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;

/**
 * Migración de validate_email() de user_find_logic.py.
 * Corrección: en Python, un email que no era texto (número, lista, objeto) provocaba un 500.
 * Aquí responde 400 INVALID_EMAIL.
 */
public final class EmailValidator {

    static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Set<String> allowedDomains;

    public EmailValidator(Set<String> allowedDomains) {
        this.allowedDomains = Set.copyOf(allowedDomains);
    }

    public String validate(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            throw new ApiException(ErrorCatalog.EMAIL_REQUIRED);
        }
        if (!raw.isTextual()) {
            throw new ApiException(ErrorCatalog.INVALID_EMAIL);
        }

        String email = raw.asText().strip().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) {
            throw new ApiException(ErrorCatalog.EMAIL_REQUIRED);
        }
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new ApiException(ErrorCatalog.INVALID_EMAIL);
        }

        String domain = domainOf(email);
        if (!allowedDomains.contains(domain)) {
            throw new ApiException(ErrorCatalog.EMAIL_DOMAIN_NOT_ALLOWED);
        }
        return email;
    }

    public static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1);
    }
}
