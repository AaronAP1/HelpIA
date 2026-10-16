package pe.com.pacifico.helpia.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.config.RateLimitSettings;
import pe.com.pacifico.helpia.domain.OperationType;

class RateLimitServiceTest {

    private static final RateLimitSettings SETTINGS = new RateLimitSettings(true, "t", "", "", 3, 3, 24);

    private final InMemoryStore store = new InMemoryStore();

    @Test
    void given_tresDesbloqueos_when_cuartoEnElMismoDia_then_rateLimitExceeded() {
        RateLimitService service = new RateLimitService(SETTINGS, store, at("2026-10-14T15:00:00Z"));
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> service.acquire(OperationType.DESBLOQUEO, "mjperez"));
        }

        ApiException e = assertThrows(ApiException.class, () -> service.acquire(OperationType.DESBLOQUEO, "mjperez"));
        assertEquals(ErrorCatalog.RATE_LIMIT_EXCEEDED, e.error());
    }

    @Test
    void given_limiteAlcanzado_when_cambiaElDiaEnLima_then_permite() {
        RateLimitService hoy = new RateLimitService(SETTINGS, store, at("2026-10-14T15:00:00Z"));
        for (int i = 0; i < 3; i++) {
            hoy.acquire(OperationType.RESETEO, "mjperez");
        }
        // 2026-10-15 05:30 UTC = 00:30 en Lima (UTC-5): nuevo día.
        RateLimitService manana = new RateLimitService(SETTINGS, store, at("2026-10-15T05:30:00Z"));

        assertDoesNotThrow(() -> manana.acquire(OperationType.RESETEO, "mjperez"));
    }

    @Test
    void given_cambioDeCelular_when_otroAntesDe24h_then_rateLimitExceeded() {
        Instant t0 = Instant.parse("2026-10-14T15:00:00Z");
        new RateLimitService(SETTINGS, store, Clock.fixed(t0, ZoneOffset.UTC)).acquire(OperationType.ACTUALIZACION_CELULAR, "mjperez");

        RateLimitService despues = new RateLimitService(SETTINGS, store, Clock.fixed(t0.plus(Duration.ofHours(23)), ZoneOffset.UTC));
        ApiException e = assertThrows(ApiException.class, () -> despues.acquire(OperationType.ACTUALIZACION_CELULAR, "mjperez"));
        assertEquals(ErrorCatalog.RATE_LIMIT_EXCEEDED, e.error());

        RateLimitService dosDias = new RateLimitService(SETTINGS, store, Clock.fixed(t0.plus(Duration.ofHours(25)), ZoneOffset.UTC));
        assertDoesNotThrow(() -> dosDias.acquire(OperationType.ACTUALIZACION_CELULAR, "mjperez"));
    }

    @Test
    void given_limitesDeshabilitados_when_acquire_then_noUsaStore() {
        RateLimitSettings off = new RateLimitSettings(false, "t", "", "", 3, 3, 24);
        RateLimitService service = new RateLimitService(off, null, Clock.systemUTC());

        assertDoesNotThrow(() -> service.acquire(OperationType.DESBLOQUEO, "mjperez"));
    }

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    static final class InMemoryStore implements RateLimitStore {
        private final Map<String, Integer> counters = new HashMap<>();
        private final Map<String, Instant> timestamps = new HashMap<>();

        @Override
        public int incrementAndGet(String partitionKey, String rowKey) {
            return counters.merge(partitionKey + "|" + rowKey, 1, Integer::sum);
        }

        @Override
        public Optional<Instant> getTimestamp(String partitionKey, String rowKey) {
            return Optional.ofNullable(timestamps.get(partitionKey + "|" + rowKey));
        }

        @Override
        public void putTimestamp(String partitionKey, String rowKey, Instant value) {
            timestamps.put(partitionKey + "|" + rowKey, value);
        }
    }
}
