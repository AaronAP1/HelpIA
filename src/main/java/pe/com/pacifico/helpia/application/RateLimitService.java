package pe.com.pacifico.helpia.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.config.RateLimitSettings;
import pe.com.pacifico.helpia.domain.OperationType;

/**
 * Límites de los flujos aprobados, aplicados en el servidor (no solo en el bot):
 * - desbloqueo y reseteo: máximo N por día calendario (hora de Lima);
 * - cambio de celular: uno cada N horas.
 * Durable Entities no existe en Java, por eso se usa Table Storage.
 *
 * Decisión a validar: hoy se cuenta cada solicitud aceptada, no cada operación exitosa.
 */
public final class RateLimitService {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final String PHONE_ROW_KEY = "CELULAR-ULTIMO-CAMBIO";

    private final RateLimitSettings settings;
    private final RateLimitStore store;
    private final Clock clock;

    public RateLimitService(RateLimitSettings settings, RateLimitStore store, Clock clock) {
        this.settings = settings;
        this.store = store;
        this.clock = clock;
    }

    public void acquire(OperationType type, String usuario) {
        if (!settings.enabled()) {
            return;
        }
        switch (type) {
            case DESBLOQUEO -> checkDaily(type, usuario, settings.maxUnlocksPerDay());
            case RESETEO -> checkDaily(type, usuario, settings.maxResetsPerDay());
            case ACTUALIZACION_CELULAR -> checkPhoneWindow(usuario);
        }
    }

    private void checkDaily(OperationType type, String usuario, int max) {
        String day = LocalDate.now(clock.withZone(LIMA)).format(DateTimeFormatter.BASIC_ISO_DATE);
        int count = store.incrementAndGet(usuario, type.name() + "-" + day);
        if (count > max) {
            throw new ApiException(ErrorCatalog.RATE_LIMIT_EXCEEDED);
        }
    }

    private void checkPhoneWindow(String usuario) {
        Instant now = clock.instant();
        Optional<Instant> last = store.getTimestamp(usuario, PHONE_ROW_KEY);
        Duration window = Duration.ofHours(settings.hoursBetweenPhoneChanges());
        if (last.isPresent() && last.get().plus(window).isAfter(now)) {
            throw new ApiException(ErrorCatalog.RATE_LIMIT_EXCEEDED);
        }
        store.putTimestamp(usuario, PHONE_ROW_KEY, now);
    }
}
