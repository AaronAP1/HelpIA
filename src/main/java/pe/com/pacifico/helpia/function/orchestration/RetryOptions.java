package pe.com.pacifico.helpia.function.orchestration;

import com.microsoft.durabletask.RetryPolicy;
import com.microsoft.durabletask.TaskOptions;
import java.time.Duration;
import pe.com.pacifico.helpia.domain.OrchestrationTimings;

/** "RetryOptions" del diagrama: políticas de reintento de las activities. */
final class RetryOptions {

    private RetryOptions() {
    }

    /** Llamadas a Azure Automation: 3 intentos, 2 s, 4 s. */
    static TaskOptions automation() {
        RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(2));
        policy.setBackoffCoefficient(2.0);
        policy.setMaxRetryInterval(Duration.ofSeconds(20));
        return new TaskOptions(policy);
    }

    /** Callback al bot: por defecto 5 intentos con backoff exponencial (5 s, 10 s, 20 s, 40 s). */
    static TaskOptions callback(OrchestrationTimings timings) {
        RetryPolicy policy = new RetryPolicy(timings.callbackMaxAttempts(),
                Duration.ofSeconds(timings.callbackFirstRetrySeconds()));
        policy.setBackoffCoefficient(2.0);
        policy.setMaxRetryInterval(Duration.ofMinutes(5));
        return new TaskOptions(policy);
    }
}
