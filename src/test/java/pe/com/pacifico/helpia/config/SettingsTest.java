package pe.com.pacifico.helpia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.Env;
import pe.com.pacifico.helpia.common.ErrorCatalog;

class SettingsTest {

    @Test
    void given_bypassLocalEnAzure_when_load_then_bloqueado() {
        Env env = env(Map.of("JWT_LOCAL_BYPASS", "true", "WEBSITE_SITE_NAME", "helpia-functions"));

        ApiException e = assertThrows(ApiException.class, () -> JwtSettings.load(env));
        assertEquals(ErrorCatalog.INSECURE_CONFIGURATION_BLOCKED, e.error());
    }

    @Test
    void given_configuracionJwtIncompleta_when_load_then_configurationError() {
        Env env = env(Map.of("JWT_TENANT_ID", "11111111-1111-1111-1111-111111111111"));

        ApiException e = assertThrows(ApiException.class, () -> JwtSettings.load(env));
        assertEquals(ErrorCatalog.JWT_CONFIGURATION_ERROR, e.error());
    }

    /** En Python una lista vacía aceptaba cualquier dominio. */
    @Test
    void given_dominiosVacios_when_load_then_fallaCerrado() {
        ApiException e = assertThrows(ApiException.class,
                () -> EmailDomainSettings.load(env(Map.of("ALLOWED_EMAIL_DOMAINS", " , "))));
        assertEquals(ErrorCatalog.CONFIGURATION_ERROR, e.error());
    }

    /** El simulador local nunca puede reemplazar a Graph en Azure. */
    @Test
    void given_graphSimuladoEnAzure_when_load_then_usaGraphReal() {
        Env azure = env(Map.of("WEBSITE_SITE_NAME", "helpia-functions", "GRAPH_BASE_URL", "http://simulador:8080/graph/v1.0"));

        assertEquals(ServiceEndpoints.GRAPH_DEFAULT, ServiceEndpoints.load(azure).graphBaseUrl());
    }

    @Test
    void given_graphSimuladoEnLocal_when_load_then_loUsa() {
        Env local = env(Map.of("GRAPH_BASE_URL", "http://simulador:8080/graph/v1.0/"));

        assertEquals("http://simulador:8080/graph/v1.0", ServiceEndpoints.load(local).graphBaseUrl());
    }

    @Test
    void given_automationSimuladoEnAzure_when_load_then_usaArmReal() {
        Env azure = env(Map.of("WEBSITE_SITE_NAME", "helpia-functions", "ARM_BASE_URL", "http://simulador:8081"));

        assertEquals(ServiceEndpoints.ARM_DEFAULT, ServiceEndpoints.load(azure).armBaseUrl());
    }

    @Test
    void given_dosTenants_when_loadJwt_then_aceptaAmbos() {
        JwtSettings settings = JwtSettings.load(env(Map.of(
                "JWT_TENANT_ID", "11111111-1111-1111-1111-111111111111, 55555555-5555-5555-5555-555555555555",
                "JWT_ALLOWED_AUDIENCES", "api://app",
                "JWT_ALLOWED_CLIENT_IDS", "33333333-3333-3333-3333-333333333333")));

        assertEquals(2, settings.tenantIds().size());
        assertEquals(2, settings.jwksUrls().size());
        assertEquals("", settings.operationsPermission());
    }

    @Test
    void given_unTenantInvalido_when_loadJwt_then_configurationError() {
        Env env = env(Map.of(
                "JWT_TENANT_ID", "11111111-1111-1111-1111-111111111111,no-es-guid",
                "JWT_ALLOWED_AUDIENCES", "api://app",
                "JWT_ALLOWED_CLIENT_IDS", "33333333-3333-3333-3333-333333333333"));

        ApiException e = assertThrows(ApiException.class, () -> JwtSettings.load(env));
        assertEquals(ErrorCatalog.JWT_CONFIGURATION_ERROR, e.error());
    }

    @Test
    void given_secretoCorto_when_loadCallback_then_configurationError() {
        Env env = env(Map.of("CALLBACK_BASE_URL", "https://bot.example.com", "CALLBACK_HMAC_SECRET", "corto"));

        assertThrows(ApiException.class, () -> CallbackSettings.load(env));
    }

    /** HTTP sin TLS solo para el simulador del bot en local; nunca en Azure. */
    @Test
    void given_callbackHttp_when_load_then_soloEnLocal() {
        String secret = "s".repeat(32);
        Env local = env(Map.of("APP_ENV", "local", "CALLBACK_BASE_URL", "http://simulador-bot:8082/", "CALLBACK_HMAC_SECRET", secret));
        Env azure = env(Map.of("APP_ENV", "local", "WEBSITE_SITE_NAME", "helpia-functions",
                "CALLBACK_BASE_URL", "http://simulador-bot:8082", "CALLBACK_HMAC_SECRET", secret));
        Env sinAppEnv = env(Map.of("CALLBACK_BASE_URL", "http://simulador-bot:8082", "CALLBACK_HMAC_SECRET", secret));

        assertEquals("http://simulador-bot:8082", CallbackSettings.load(local).baseUrl());
        assertThrows(ApiException.class, () -> CallbackSettings.load(azure));
        assertThrows(ApiException.class, () -> CallbackSettings.load(sinAppEnv));
    }

    private static Env env(Map<String, String> values) {
        Map<String, String> copy = new HashMap<>(values);
        return new Env(copy::get);
    }
}
