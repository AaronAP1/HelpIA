package pe.com.pacifico.helpia.function.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PollingPolicyTest {

    @Test
    void given_esperaInicial_when_next_then_duplicaHastaElMaximo() {
        Duration max = Duration.ofSeconds(30);
        assertEquals(Duration.ofSeconds(10), PollingPolicy.next(Duration.ofSeconds(5), max));
        assertEquals(Duration.ofSeconds(30), PollingPolicy.next(Duration.ofSeconds(20), max));
        assertEquals(Duration.ofSeconds(30), PollingPolicy.next(Duration.ofSeconds(30), max));
    }

    @Test
    void given_plazo_when_fitsBefore_then_respetaElLimite() {
        Instant now = Instant.parse("2026-10-14T15:00:00Z");
        Instant deadline = now.plusSeconds(30);
        assertTrue(PollingPolicy.fitsBefore(now, Duration.ofSeconds(30), deadline));
        assertFalse(PollingPolicy.fitsBefore(now, Duration.ofSeconds(31), deadline));
    }
}
