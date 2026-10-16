package pe.com.pacifico.helpia.common;

/**
 * Catálogo de errores. Los códigos, estados HTTP y mensajes heredados del servicio Python
 * (authentication.py, router.py y user_find_logic.py) se mantienen idénticos para no romper al consumidor.
 * Agregados: CONFIGURATION_ERROR (lista de dominios vacía, que en Python aceptaba todo) y los de la Etapa 3.
 */
public enum ErrorCatalog {

    // authentication.py
    JWT_REQUIRED(401, "Debe enviar un JWT mediante Authorization: Bearer <token>."),
    INVALID_JWT(401, "El JWT no es válido."),
    JWT_EXPIRED(401, "El JWT ha vencido."),
    JWT_NOT_ACTIVE(401, "El JWT todavía no es válido."),
    INVALID_JWT_AUDIENCE(401, "La audiencia del JWT no es válida."),
    INVALID_JWT_ISSUER(401, "El emisor del JWT no está permitido."),
    CLIENT_NOT_ALLOWED(403, "La aplicación consumidora no está autorizada."),
    INSUFFICIENT_PERMISSION(403, "El JWT no contiene el permiso requerido."),
    JWT_CONFIGURATION_ERROR(503, "La autenticación JWT no está configurada correctamente."),
    INSECURE_CONFIGURATION_BLOCKED(503, "La configuración de seguridad del servicio no es válida."),
    JWT_KEYS_UNAVAILABLE(503, "No fue posible validar el JWT temporalmente."),
    GRAPH_CONFIGURATION_ERROR(503, "AZURE_TENANT_ID no está configurado correctamente."),
    GRAPH_AUTHENTICATION_UNAVAILABLE(503, "No fue posible autenticarse en Microsoft Graph."),

    // router.py
    REQUEST_TOO_LARGE(413, "La solicitud es demasiado grande."),
    UNSUPPORTED_MEDIA_TYPE(415, "El Content-Type debe ser application/json."),
    INVALID_JSON(400, "El cuerpo JSON no es válido."),

    // user_find_logic.py
    EMAIL_REQUIRED(400, "Debe enviar el parámetro email."),
    INVALID_EMAIL(400, "El formato del correo no es válido."),
    EMAIL_DOMAIN_NOT_ALLOWED(400, "El dominio del correo no está permitido."),
    GRAPH_PERMISSION_DENIED(503, "La identidad del servicio no tiene permiso para leer usuarios."),
    GRAPH_THROTTLED(503, "Microsoft Graph limitó temporalmente las consultas."),
    GRAPH_TIMEOUT(504, "Microsoft Graph tardó demasiado en responder."),
    GRAPH_UNAVAILABLE(502, "Microsoft Graph no está disponible temporalmente."),
    INVALID_GRAPH_RESPONSE(502, "Microsoft Graph devolvió una respuesta no válida."),
    GRAPH_ERROR(502, "Microsoft Graph rechazó la consulta."),
    AMBIGUOUS_EMAIL(409, "El correo coincide con más de un usuario."),

    // Etapa 3: desbloqueo, reseteo y cambio de celular
    USER_REQUIRED(400, "Debe enviar el usuario de red."),
    INVALID_USER(400, "El usuario de red no tiene un formato válido."),
    INVALID_DOMAIN(400, "El dominio indicado no es válido."),
    INVALID_PHONE(400, "El número celular debe tener 9 dígitos y empezar con 9."),
    USER_NOT_FOUND(404, "El usuario de red no existe."),
    DOMAIN_REQUIRED(409, "El usuario tiene más de una cuenta; debe indicar el dominio."),
    ACCOUNT_DISABLED(409, "La cuenta no se encuentra habilitada."),
    ACCOUNT_OUT_OF_SCOPE(403, "La cuenta no está dentro del alcance de autogestión."),
    USER_MISMATCH(403, "El usuario del token no corresponde a la cuenta solicitada."),
    RATE_LIMIT_EXCEEDED(429, "Se alcanzó el máximo de intentos permitidos."),
    OPERATION_NOT_FOUND(404, "No existe una operación con ese identificador."),
    ORCHESTRATION_UNAVAILABLE(503, "No fue posible registrar la operación."),

    CONFIGURATION_ERROR(503, "El servicio no está configurado correctamente."),
    INTERNAL_ERROR(500, "Ocurrió un error interno.");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCatalog(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
