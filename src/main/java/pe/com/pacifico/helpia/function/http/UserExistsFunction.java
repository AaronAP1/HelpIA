package pe.com.pacifico.helpia.function.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.Optional;
import pe.com.pacifico.helpia.application.EmailValidator;
import pe.com.pacifico.helpia.application.UserExistsResult;
import pe.com.pacifico.helpia.common.CorrelationIdResolver;
import pe.com.pacifico.helpia.function.Dependencies;

/**
 * Validación de existencia del usuario (enrolamiento). Migrado de router.user_exists().
 * Se exponen dos rutas con la misma lógica:
 * - POST /api/v1/entra/users/exists   → contrato actual del servicio Python (no romper al consumidor).
 * - POST /api/usuarios/validar-existencia → ruta del diagrama de arquitectura.
 * Cuando el consumidor migre a la ruta nueva, la antigua puede retirarse.
 */
public class UserExistsFunction {

    @FunctionName("UserExists")
    public HttpResponseMessage legacyRoute(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "v1/entra/users/exists") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return handle(request, context);
    }

    @FunctionName("ValidarExistenciaUsuario")
    public HttpResponseMessage architectureRoute(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "usuarios/validar-existencia") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return handle(request, context);
    }

    private HttpResponseMessage handle(HttpRequestMessage<Optional<String>> request, ExecutionContext context) {
        String correlationId = CorrelationIdResolver.resolve(request.getHeaders());
        return HttpSupport.handle(request, context, correlationId, () -> {
            Dependencies deps = Dependencies.get();
            deps.jwtValidator().validate(request.getHeaders());

            JsonNode body = HttpSupport.readJsonObject(request);
            String email = deps.emailValidator().validate(body.get("email"));
            UserExistsResult result = deps.userLookup().findByEmail(email, correlationId);

            context.getLogger().info("Usuario validado; domain=" + EmailValidator.domainOf(email)
                    + "; exists=" + result.exists() + "; correlation-id=" + correlationId);
            return ResponseFactory.json(request, 200, result.toResponseBody(), correlationId);
        });
    }
}
