package pe.com.pacifico.helpia.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.auth.AuthenticatedCaller;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.domain.DirectoryUser;
import pe.com.pacifico.helpia.domain.OperationType;

class AccountResolverTest {

    private static final AuthenticatedCaller APP = new AuthenticatedCaller("bot", "s", null, Set.of(), Set.of("EmailValidation.Read"));

    private final FakeUserDirectory directory = new FakeUserDirectory();
    private final AccountResolver resolver = new AccountResolver(directory,
            new DefaultAccountScopePolicy(Set.of("pacifico.com.pe", "pacificovida.com.pe")));

    private final DirectoryUser principal = FakeUserDirectory.user("mjperez@pacifico.com.pe", null, "mjperez", "pacificoseguros.com", true);
    private final DirectoryUser alterna = FakeUserDirectory.user("mjperez@pacificovida.com.pe", null, "mjperez", "pacificovida.local", true);

    @Test
    void given_desbloqueoConDosCuentas_when_resolve_then_devuelveAmbosDominios() {
        directory.bySam.addAll(List.of(principal, alterna));

        List<String> dominios = resolver.resolve(request(OperationType.DESBLOQUEO, null), APP, "c1");

        assertEquals(List.of("pacificoseguros.com", "pacificovida.local"), dominios);
    }

    @Test
    void given_reseteoConDosCuentasSinDominio_when_resolve_then_domainRequired() {
        directory.bySam.addAll(List.of(principal, alterna));

        assertError(ErrorCatalog.DOMAIN_REQUIRED, request(OperationType.RESETEO, null), APP);
    }

    @Test
    void given_reseteoConDominioElegido_when_resolve_then_usaSoloEsaCuenta() {
        directory.bySam.addAll(List.of(principal, alterna));

        assertEquals(List.of("pacificovida.local"), resolver.resolve(request(OperationType.RESETEO, "pacificovida"), APP, "c1"));
    }

    @Test
    void given_usuarioInexistente_when_resolve_then_userNotFound() {
        assertError(ErrorCatalog.USER_NOT_FOUND, request(OperationType.RESETEO, null), APP);
    }

    @Test
    void given_cuentaDeshabilitada_when_resolve_then_accountDisabled() {
        directory.bySam.add(FakeUserDirectory.user("mjperez@pacifico.com.pe", null, "mjperez", "d", false));

        assertError(ErrorCatalog.ACCOUNT_DISABLED, request(OperationType.RESETEO, null), APP);
    }

    @Test
    void given_cuentaCloudOnly_when_resolve_then_outOfScope() {
        directory.bySam.add(new DirectoryUser("id", "svc@pacifico.com.pe", null, "svc", null, false, true));

        assertError(ErrorCatalog.ACCOUNT_OUT_OF_SCOPE, request(OperationType.RESETEO, null), APP);
    }

    @Test
    void given_tokenDeOtroUsuario_when_resolve_then_userMismatch() {
        directory.bySam.add(principal);
        AuthenticatedCaller otro = new AuthenticatedCaller("bot", "s", "otro@pacifico.com.pe", Set.of("EmailValidation.Read"), Set.of());

        assertError(ErrorCatalog.USER_MISMATCH, request(OperationType.RESETEO, null), otro);
    }

    @Test
    void given_tokenDelMismoUsuario_when_resolve_then_permite() {
        directory.bySam.add(principal);
        AuthenticatedCaller mismo = new AuthenticatedCaller("bot", "s", "mjperez@pacifico.com.pe", Set.of("EmailValidation.Read"), Set.of());

        assertEquals(List.of("pacificoseguros.com"), resolver.resolve(request(OperationType.RESETEO, null), mismo, "c1"));
    }

    private static OperationRequest request(OperationType type, String dominio) {
        return new OperationRequest(type, "mjperez", dominio, null, "TEAMS");
    }

    private void assertError(ErrorCatalog expected, OperationRequest request, AuthenticatedCaller caller) {
        ApiException e = assertThrows(ApiException.class, () -> resolver.resolve(request, caller, "c1"));
        assertEquals(expected, e.error());
    }
}
