package pe.com.pacifico.helpia.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.common.Headers;
import pe.com.pacifico.helpia.config.JwtSettings;

/**
 * Validación de JWT de Entra ID. Migración de require_jwt() de authentication.py.
 * Se preservan las reglas de seguridad de Python:
 * RS256 únicamente, tokens hasta 16 KB, claims exp/iat/nbf/iss/aud/tid obligatorios, 60 s de tolerancia de reloj,
 * issuer v1 o v2 de un tenant configurado (y coherente con tid), azp/appid en lista blanca y permiso en scp o roles.
 * Las claves del JWKS se cachean 5 minutos y se refrescan si llega un kid desconocido (rotación de Entra).
 * Con varios tenants (Teams y WhatsApp) se consultan las claves de todos; el emisor lo decide iss/tid.
 * Health y validar-existencia exigen JWT_REQUIRED_PERMISSION; las operaciones de la Etapa 3 exigen
 * JWT_REQUIRED_PERMISSION_OPERACIONES (validateForOperations).
 */
public final class JwtValidator {

    static final int MAX_JWT_BYTES = 16_384;
    static final long CLOCK_SKEW_SECONDS = 60;
    static final List<String> REQUIRED_CLAIMS = List.of("exp", "iat", "nbf", "iss", "aud", "tid");

    private static final Pattern BEARER = Pattern.compile("^Bearer\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);

    private final JwtSettings settings;
    private final DefaultJWTProcessor<SecurityContext> processor;
    private final Clock clock;

    public JwtValidator(JwtSettings settings, JWKSource<SecurityContext> keySource, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        if (keySource == null) {
            this.processor = null;
        } else {
            DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
            p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
            // Los claims se validan manualmente para devolver los mismos códigos de error que Python.
            p.setJWTClaimsSetVerifier((claims, context) -> { });
            this.processor = p;
        }
    }

    public static JwtValidator forEntraId(JwtSettings settings) {
        if (settings.localBypass()) {
            return new JwtValidator(settings, null, Clock.systemUTC());
        }
        try {
            List<JWKSource<SecurityContext>> sources = new ArrayList<>();
            for (String url : settings.jwksUrls()) {
                sources.add(JWKSourceBuilder
                        .<SecurityContext>create(
                                URI.create(url).toURL(),
                                new DefaultResourceRetriever(5_000, 5_000, 512 * 1024))
                        .cache(Duration.ofMinutes(5).toMillis(), Duration.ofSeconds(15).toMillis())
                        .build());
            }
            return new JwtValidator(settings, sources.size() == 1 ? sources.get(0) : allOf(sources), Clock.systemUTC());
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new ApiException(ErrorCatalog.JWT_CONFIGURATION_ERROR, e);
        }
    }

    /** Une las claves de varios tenants. Si ninguno responde, se propaga la falla (503 JWT_KEYS_UNAVAILABLE). */
    static JWKSource<SecurityContext> allOf(List<JWKSource<SecurityContext>> sources) {
        return (selector, context) -> {
            List<JWK> keys = new ArrayList<>();
            KeySourceException failure = null;
            for (JWKSource<SecurityContext> source : sources) {
                try {
                    keys.addAll(source.get(selector, context));
                } catch (KeySourceException e) {
                    failure = e;
                }
            }
            if (keys.isEmpty() && failure != null) {
                throw failure;
            }
            return keys;
        };
    }

    /** Health y validar-existencia: exige JWT_REQUIRED_PERMISSION. */
    public AuthenticatedCaller validate(Map<String, String> headers) {
        return validate(headers, settings.requiredPermission());
    }

    /** Desbloqueo, reseteo, celular y consulta de operaciones: exige JWT_REQUIRED_PERMISSION_OPERACIONES. */
    public AuthenticatedCaller validateForOperations(Map<String, String> headers) {
        if (!settings.localBypass() && settings.operationsPermission().isEmpty()) {
            throw new ApiException(ErrorCatalog.JWT_CONFIGURATION_ERROR);
        }
        return validate(headers, settings.operationsPermission());
    }

    private AuthenticatedCaller validate(Map<String, String> headers, String requiredPermission) {
        String token = extractBearerToken(headers);

        if (settings.localBypass()) {
            return callerFromUnverifiedToken(token);
        }

        JWTClaimsSet claims;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new ApiException(ErrorCatalog.INVALID_JWT);
            }
            claims = processor.process(jwt, null);
        } catch (ApiException e) {
            throw e;
        } catch (KeySourceException e) {
            throw new ApiException(ErrorCatalog.JWT_KEYS_UNAVAILABLE, e);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new ApiException(ErrorCatalog.INVALID_JWT, e);
        }

        validateRegisteredClaims(claims);
        validateAuthorizationClaims(claims, requiredPermission);
        return toCaller(claims);
    }

    static String extractBearerToken(Map<String, String> headers) {
        String authorization = Headers.get(headers, "Authorization");
        Matcher matcher = BEARER.matcher(authorization == null ? "" : authorization.trim());
        if (!matcher.matches()) {
            throw new ApiException(ErrorCatalog.JWT_REQUIRED);
        }
        String token = matcher.group(1);
        if (token.getBytes(StandardCharsets.UTF_8).length > MAX_JWT_BYTES) {
            throw new ApiException(ErrorCatalog.INVALID_JWT);
        }
        return token;
    }

    /** Mismo orden que PyJWT: claims obligatorios, iat/nbf, exp y audiencia. */
    private void validateRegisteredClaims(JWTClaimsSet claims) {
        for (String name : REQUIRED_CLAIMS) {
            if (claims.getClaim(name) == null) {
                throw new ApiException(ErrorCatalog.INVALID_JWT);
            }
        }
        Instant now = clock.instant();
        Instant latestValidStart = now.plusSeconds(CLOCK_SKEW_SECONDS);
        if (isAfter(claims.getIssueTime(), latestValidStart) || isAfter(claims.getNotBeforeTime(), latestValidStart)) {
            throw new ApiException(ErrorCatalog.JWT_NOT_ACTIVE);
        }
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.toInstant().plusSeconds(CLOCK_SKEW_SECONDS).isBefore(now)) {
            throw new ApiException(ErrorCatalog.JWT_EXPIRED);
        }
        List<String> audiences = claims.getAudience();
        if (audiences == null || audiences.stream().noneMatch(settings.audiences()::contains)) {
            throw new ApiException(ErrorCatalog.INVALID_JWT_AUDIENCE);
        }
    }

    /** Equivale a _validate_authorization_claims() de Python. */
    private void validateAuthorizationClaims(JWTClaimsSet claims, String requiredPermission) {
        String issuer = stringClaim(claims, "iss");
        String tenantId = stringClaim(claims, "tid").toLowerCase(Locale.ROOT);
        if (!settings.tenantIds().contains(tenantId) || !settings.validIssuers(tenantId).contains(issuer)) {
            throw new ApiException(ErrorCatalog.INVALID_JWT_ISSUER);
        }

        String clientId = clientId(claims);
        if (!settings.allowedClientIds().contains(clientId)) {
            throw new ApiException(ErrorCatalog.CLIENT_NOT_ALLOWED);
        }

        Set<String> granted = new HashSet<>(scopes(claims));
        granted.addAll(roles(claims));
        if (!granted.contains(requiredPermission)) {
            throw new ApiException(ErrorCatalog.INSUFFICIENT_PERMISSION);
        }
    }

    private static AuthenticatedCaller toCaller(JWTClaimsSet claims) {
        String upn = firstNonEmpty(
                stringClaim(claims, "upn"),
                stringClaim(claims, "preferred_username"),
                stringClaim(claims, "unique_name"));
        return new AuthenticatedCaller(
                clientId(claims),
                stringClaim(claims, "sub"),
                upn.isEmpty() ? null : upn.toLowerCase(Locale.ROOT),
                scopes(claims),
                roles(claims));
    }

    /** Solo local (JWT_LOCAL_BYPASS=true, bloqueado en Azure): lee claims sin verificar firma. */
    private static AuthenticatedCaller callerFromUnverifiedToken(String token) {
        try {
            return toCaller(JWTParser.parse(token).getJWTClaimsSet());
        } catch (ParseException e) {
            return AuthenticatedCaller.localBypass();
        }
    }

    private static String clientId(JWTClaimsSet claims) {
        return firstNonEmpty(stringClaim(claims, "azp"), stringClaim(claims, "appid")).toLowerCase(Locale.ROOT);
    }

    private static Set<String> scopes(JWTClaimsSet claims) {
        Set<String> result = new HashSet<>();
        for (String scope : stringClaim(claims, "scp").split("\\s+")) {
            if (!scope.isEmpty()) {
                result.add(scope);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> roles(JWTClaimsSet claims) {
        Object raw = claims.getClaim("roles");
        Set<String> result = new HashSet<>();
        if (raw instanceof String s) {
            result.add(s);
        } else if (raw instanceof Collection<?> list) {
            list.forEach(r -> result.add(String.valueOf(r)));
        }
        return Set.copyOf(result);
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static boolean isAfter(Date value, Instant limit) {
        return value != null && value.toInstant().isAfter(limit);
    }
}
