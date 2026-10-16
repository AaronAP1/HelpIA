package pe.com.pacifico.helpia.config;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.common.ErrorCatalog;

/**
 * Configuración de validación JWT. Equivale a _load_jwt_configuration() de Python, con dos extensiones
 * para la arquitectura v4:
 * - JWT_TENANT_ID acepta varios tenants separados por coma: el diagrama tiene dos emisores
 *   (producto de Teams con token de usuario y producto de WhatsApp con token de aplicación).
 * - JWT_REQUIRED_PERMISSION_OPERACIONES: permiso propio para desbloqueo, reseteo y celular, distinto del de
 *   health y validar-existencia. Puede quedar vacío mientras la Etapa 3 no esté publicada: esas operaciones
 *   responden 503 JWT_CONFIGURATION_ERROR (falla cerrado) y el resto sigue funcionando.
 */
public record JwtSettings(
        Set<String> tenantIds,
        Set<String> audiences,
        Set<String> allowedClientIds,
        String requiredPermission,
        String operationsPermission,
        boolean localBypass) {

    public static final String DEFAULT_REQUIRED_PERMISSION = "EmailValidation.Read";
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    /**
     * Carga y valida. Lanza ApiException con los mismos códigos que Python cuando la configuración
     * es incompleta (503 JWT_CONFIGURATION_ERROR) o insegura (503 INSECURE_CONFIGURATION_BLOCKED).
     */
    public static JwtSettings load(Env env) {
        boolean bypass = env.isTrue("JWT_LOCAL_BYPASS");
        if (bypass && (env.isRunningInAzure() || !"local".equalsIgnoreCase(env.get("APP_ENV", "local")))) {
            throw new ApiException(ErrorCatalog.INSECURE_CONFIGURATION_BLOCKED);
        }

        Set<String> tenantIds = env.getList("JWT_TENANT_ID", true);
        Set<String> audiences = env.getList("JWT_ALLOWED_AUDIENCES", false);
        Set<String> clientIds = env.getList("JWT_ALLOWED_CLIENT_IDS", true);
        String permission = env.get("JWT_REQUIRED_PERMISSION", DEFAULT_REQUIRED_PERMISSION);
        String operationsPermission = env.get("JWT_REQUIRED_PERMISSION_OPERACIONES");

        boolean validTenants = !tenantIds.isEmpty() && tenantIds.stream().allMatch(id -> GUID.matcher(id).matches());
        boolean validClientIds = !clientIds.isEmpty() && clientIds.stream().allMatch(id -> GUID.matcher(id).matches());
        if (!bypass && (!validTenants || audiences.isEmpty() || !validClientIds || permission.isEmpty())) {
            throw new ApiException(ErrorCatalog.JWT_CONFIGURATION_ERROR);
        }
        return new JwtSettings(tenantIds, audiences, clientIds, permission, operationsPermission, bypass);
    }

    public Set<String> validIssuers(String tenantId) {
        return Set.of(
                "https://login.microsoftonline.com/" + tenantId + "/v2.0",
                "https://sts.windows.net/" + tenantId + "/");
    }

    public List<String> jwksUrls() {
        return tenantIds.stream()
                .map(id -> "https://login.microsoftonline.com/" + id + "/discovery/v2.0/keys")
                .toList();
    }
}
