package pe.com.pacifico.helpia.function.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import pe.com.pacifico.helpia.common.CorrelationIdResolver;
import pe.com.pacifico.helpia.function.Dependencies;

/** GET /api/health — diagrama: "GET /api/health (real)". Migrado de router.health(). Exige JWT, igual que Python. */
public class HealthFunction {

    static final String SERVICE_NAME = "entra-user-validation-api";

    @FunctionName("Health")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION,
                    route = "health") HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        String correlationId = CorrelationIdResolver.resolve(request.getHeaders());
        return HttpSupport.handle(request, context, correlationId, () -> {
            Dependencies.get().jwtValidator().validate(request.getHeaders());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "OK");
            body.put("service", SERVICE_NAME);
            return ResponseFactory.json(request, 200, body, correlationId);
        });
    }
}
