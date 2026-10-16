package pe.com.pacifico.helpia.function.orchestration;

import java.time.Duration;
import java.time.Instant;

/** Espera entre consultas al job: crece x2 hasta un máximo. Lógica pura (testeable sin Durable). */
public final class PollingPolicy {

    private PollingPolicy() {
    }

    public static Duration next(Duration current, Duration max) {
        Duration doubled = current.multipliedBy(2);
        return doubled.compareTo(max) > 0 ? max : doubled;
    }

    public static boolean fitsBefore(Instant now, Duration wait, Instant deadline) {
        return !now.plus(wait).isAfter(deadline);
    }
}
