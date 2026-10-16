package pe.com.pacifico.helpia.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.regex.Pattern;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.domain.OperationType;

/**
 * Valida el body de las operaciones de la Etapa 3.
 * Body esperado: { "usuario": "mjperez", "dominio": "opcional", "celular": "987654321 (solo celular)", "canal": "TEAMS|WHATSAPP" }
 */
public final class OperationRequestParser {

    /** sAMAccountName: máximo 20 caracteres. */
    private static final Pattern USUARIO = Pattern.compile("^[a-z0-9._-]{1,20}$");
    private static final Pattern DOMINIO = Pattern.compile("^[a-z0-9.-]{1,100}$");
    /** Acta Fase 3: celular de Perú, 9 dígitos. */
    private static final Pattern CELULAR = Pattern.compile("^9\\d{8}$");
    private static final Pattern CANAL = Pattern.compile("^[A-Z]{1,20}$");

    public OperationRequest parse(OperationType type, JsonNode body) {
        String usuario = requiredText(body.get("usuario"), ErrorCatalog.USER_REQUIRED, ErrorCatalog.INVALID_USER)
                .toLowerCase(Locale.ROOT);
        if (!USUARIO.matcher(usuario).matches()) {
            throw new ApiException(ErrorCatalog.INVALID_USER);
        }

        String dominio = optionalText(body.get("dominio"), ErrorCatalog.INVALID_DOMAIN);
        if (dominio != null) {
            dominio = dominio.toLowerCase(Locale.ROOT);
            if (!DOMINIO.matcher(dominio).matches()) {
                throw new ApiException(ErrorCatalog.INVALID_DOMAIN);
            }
        }

        String celular = null;
        if (type == OperationType.ACTUALIZACION_CELULAR) {
            celular = requiredText(body.get("celular"), ErrorCatalog.INVALID_PHONE, ErrorCatalog.INVALID_PHONE)
                    .replaceAll("[\\s-]", "");
            if (!CELULAR.matcher(celular).matches()) {
                throw new ApiException(ErrorCatalog.INVALID_PHONE);
            }
        }

        String canal = optionalText(body.get("canal"), ErrorCatalog.INVALID_JSON);
        if (canal != null) {
            canal = canal.toUpperCase(Locale.ROOT);
            if (!CANAL.matcher(canal).matches()) {
                canal = null;
            }
        }
        return new OperationRequest(type, usuario, dominio, celular, canal);
    }

    private static String requiredText(JsonNode node, ErrorCatalog missing, ErrorCatalog invalid) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            throw new ApiException(missing);
        }
        if (!node.isTextual()) {
            throw new ApiException(invalid);
        }
        String value = node.asText().strip();
        if (value.isEmpty()) {
            throw new ApiException(missing);
        }
        return value;
    }

    private static String optionalText(JsonNode node, ErrorCatalog invalid) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new ApiException(invalid);
        }
        String value = node.asText().strip();
        return value.isEmpty() ? null : value;
    }
}
