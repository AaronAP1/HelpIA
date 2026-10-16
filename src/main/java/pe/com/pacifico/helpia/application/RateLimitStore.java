package pe.com.pacifico.helpia.application;

import java.time.Instant;
import java.util.Optional;

/** Persistencia de contadores de uso. Implementado sobre Azure Table Storage. */
public interface RateLimitStore {

    /** Incrementa el contador de (partitionKey, rowKey) y devuelve el nuevo valor. */
    int incrementAndGet(String partitionKey, String rowKey);

    Optional<Instant> getTimestamp(String partitionKey, String rowKey);

    void putTimestamp(String partitionKey, String rowKey, Instant value);
}
