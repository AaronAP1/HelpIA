package pe.com.pacifico.helpia.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.config.JwtSettings;

/** Replica las pruebas de seguridad del módulo authentication.py. */
class JwtValidatorTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    /** Segundo emisor del diagrama (p. ej. Teams y WhatsApp en tenants distintos). */
    private static final String TENANT_2 = "55555555-5555-5555-5555-555555555555";
    private static final String AUDIENCE = "api://22222222-2222-2222-2222-222222222222";
    private static final String CLIENT = "33333333-3333-3333-3333-333333333333";
    private static final String PERMISSION = "EmailValidation.Read";
    private static final String OPERATIONS_PERMISSION = "Identidad.Operaciones";
    private static final Instant NOW = Instant.parse("2026-10-14T15:00:00Z");

    private static RSAKey signingKey;
    private static JwtValidator validator;

    @BeforeAll
    static void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        validator = validatorWith(OPERATIONS_PERMISSION, keys());
    }

    @Test
    void given_tokenDeUsuarioValido_when_validate_then_devuelveCaller() {
        AuthenticatedCaller caller = validate(token(b -> b.claim("upn", "MJPerez@pacifico.com.pe")));

        assertTrue(caller.isUserToken());
        assertEquals("mjperez@pacifico.com.pe", caller.userPrincipalName());
        assertEquals(CLIENT, caller.clientId());
    }

    @Test
    void given_tokenDeAplicacionConRoles_when_validate_then_acepta() {
        AuthenticatedCaller caller = validate(token(b -> b.claim("scp", null).claim("roles", List.of(PERMISSION))));

        assertFalse(caller.isUserToken());
    }

    @Test
    void given_tokenV1ConAppid_when_validate_then_acepta() {
        validate(token(b -> b.issuer("https://sts.windows.net/" + TENANT + "/").claim("azp", null).claim("appid", CLIENT)));
    }

    @Test
    void given_sinAuthorization_when_validate_then_jwtRequired() {
        assertError(ErrorCatalog.JWT_REQUIRED, Map.of());
        assertError(ErrorCatalog.JWT_REQUIRED, Map.of("Authorization", "Basic abc"));
    }

    @Test
    void given_tokenVencido_when_validate_then_jwtExpired() {
        assertError(ErrorCatalog.JWT_EXPIRED, token(b -> b.expirationTime(Date.from(NOW.minusSeconds(61)))));
    }

    @Test
    void given_tokenVencidoDentroDeLaTolerancia_when_validate_then_acepta() {
        validate(token(b -> b.expirationTime(Date.from(NOW.minusSeconds(30)))));
    }

    @Test
    void given_nbfFuturo_when_validate_then_jwtNotActive() {
        assertError(ErrorCatalog.JWT_NOT_ACTIVE, token(b -> b.notBeforeTime(Date.from(NOW.plusSeconds(120)))));
    }

    @Test
    void given_sinNbfOSinTid_when_validate_then_invalidJwt() {
        assertError(ErrorCatalog.INVALID_JWT, token(b -> b.notBeforeTime(null)));
        assertError(ErrorCatalog.INVALID_JWT, token(b -> b.claim("tid", null)));
    }

    @Test
    void given_audienciaIncorrecta_when_validate_then_invalidAudience() {
        assertError(ErrorCatalog.INVALID_JWT_AUDIENCE, token(b -> b.audience("api://otra")));
    }

    @Test
    void given_emisorDeOtroTenant_when_validate_then_invalidIssuer() {
        assertError(ErrorCatalog.INVALID_JWT_ISSUER,
                token(b -> b.issuer("https://login.microsoftonline.com/99999999-9999-9999-9999-999999999999/v2.0")));
    }

    @Test
    void given_clienteNoPermitido_when_validate_then_clientNotAllowed() {
        assertError(ErrorCatalog.CLIENT_NOT_ALLOWED, token(b -> b.claim("azp", "44444444-4444-4444-4444-444444444444")));
    }

    @Test
    void given_sinPermiso_when_validate_then_insufficientPermission() {
        assertError(ErrorCatalog.INSUFFICIENT_PERMISSION, token(b -> b.claim("scp", "Otro.Permiso")));
    }

    @Test
    void given_firmaDeOtraClave_when_validate_then_invalidJwt() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("k1").generate();
        assertError(ErrorCatalog.INVALID_JWT, sign(baseClaims().build(), otherKey));
    }

    @Test
    void given_algoritmoHs256_when_validate_then_invalidJwt() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), baseClaims().build());
        jwt.sign(new MACSigner("clave-simetrica-de-al-menos-32-bytes!!"));
        assertError(ErrorCatalog.INVALID_JWT, bearer(jwt.serialize()));
    }

    @Test
    void given_tokenMayorA16Kb_when_validate_then_invalidJwt() {
        assertError(ErrorCatalog.INVALID_JWT, bearer("a".repeat(16_385)));
    }

    // ---------- varios emisores (Teams / WhatsApp) ----------

    @Test
    void given_tokenDelSegundoTenant_when_validate_then_acepta() {
        validate(token(b -> b.issuer("https://login.microsoftonline.com/" + TENANT_2 + "/v2.0").claim("tid", TENANT_2)));
    }

    @Test
    void given_issDeUnTenantYTidDeOtro_when_validate_then_invalidIssuer() {
        assertError(ErrorCatalog.INVALID_JWT_ISSUER, token(b -> b.claim("tid", TENANT_2)));
    }

    @Test
    void given_unTenantSinClaves_when_validate_then_usaLasDelOtro() {
        JWKSource<SecurityContext> caido = (selector, context) -> {
            throw new KeySourceException("tenant caído");
        };
        JwtValidator v = validatorWith(OPERATIONS_PERMISSION, JwtValidator.allOf(List.of(caido, keys())));

        v.validate(token(b -> { }));
    }

    @Test
    void given_ningunTenantResponde_when_validate_then_keysUnavailable() {
        JWKSource<SecurityContext> caido = (selector, context) -> {
            throw new KeySourceException("tenant caído");
        };
        JwtValidator v = validatorWith(OPERATIONS_PERMISSION, JwtValidator.allOf(List.of(caido, caido)));

        ApiException e = assertThrows(ApiException.class, () -> v.validate(token(b -> { })));
        assertEquals(ErrorCatalog.JWT_KEYS_UNAVAILABLE, e.error());
    }

    // ---------- permiso de operaciones (Etapa 3) ----------

    @Test
    void given_tokenConPermisoDeOperaciones_when_validateForOperations_then_acepta() {
        Map<String, String> headers = token(b -> b.claim("scp", null).claim("roles", List.of(OPERATIONS_PERMISSION)));

        validator.validateForOperations(headers);
        ApiException e = assertThrows(ApiException.class, () -> validator.validate(headers));
        assertEquals(ErrorCatalog.INSUFFICIENT_PERMISSION, e.error());
    }

    /** Un token que solo puede validar correos no puede desbloquear ni resetear. */
    @Test
    void given_tokenSoloDeConsulta_when_validateForOperations_then_insufficientPermission() {
        ApiException e = assertThrows(ApiException.class, () -> validator.validateForOperations(token(b -> { })));
        assertEquals(ErrorCatalog.INSUFFICIENT_PERMISSION, e.error());
    }

    @Test
    void given_permisoDeOperacionesNoConfigurado_when_validateForOperations_then_configurationError() {
        JwtValidator v = validatorWith("", keys());

        ApiException e = assertThrows(ApiException.class, () -> v.validateForOperations(token(b -> { })));
        assertEquals(ErrorCatalog.JWT_CONFIGURATION_ERROR, e.error());
        v.validate(token(b -> { }));
    }

    // ---------- helpers ----------

    private static JWKSource<SecurityContext> keys() {
        return new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
    }

    private static JwtValidator validatorWith(String operationsPermission, JWKSource<SecurityContext> keySource) {
        JwtSettings settings = new JwtSettings(Set.of(TENANT, TENANT_2), Set.of(AUDIENCE), Set.of(CLIENT),
                PERMISSION, operationsPermission, false);
        return new JwtValidator(settings, keySource, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://login.microsoftonline.com/" + TENANT + "/v2.0")
                .audience(AUDIENCE)
                .subject("sujeto")
                .issueTime(Date.from(NOW.minusSeconds(10)))
                .notBeforeTime(Date.from(NOW.minusSeconds(10)))
                .expirationTime(Date.from(NOW.plusSeconds(600)))
                .claim("tid", TENANT)
                .claim("azp", CLIENT)
                .claim("scp", PERMISSION);
    }

    private static Map<String, String> token(Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder builder = baseClaims();
        customizer.accept(builder);
        try {
            return sign(builder.build(), signingKey);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> sign(JWTClaimsSet claims, RSAKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return bearer(jwt.serialize());
    }

    private static Map<String, String> bearer(String token) {
        return Map.of("authorization", "Bearer " + token);
    }

    private static AuthenticatedCaller validate(Map<String, String> headers) {
        return validator.validate(headers);
    }

    private static void assertError(ErrorCatalog expected, Map<String, String> headers) {
        ApiException e = assertThrows(ApiException.class, () -> validator.validate(headers));
        assertEquals(expected, e.error());
    }
}
