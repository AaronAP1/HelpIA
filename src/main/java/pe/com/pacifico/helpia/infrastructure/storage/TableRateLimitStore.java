package pe.com.pacifico.helpia.infrastructure.storage;

import com.azure.core.credential.TokenCredential;
import com.azure.core.util.Context;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.data.tables.models.TableServiceException;
import java.time.Instant;
import java.util.Optional;
import pe.com.pacifico.helpia.application.RateLimitStore;
import pe.com.pacifico.helpia.common.ApiException;
import pe.com.pacifico.helpia.common.ErrorCatalog;
import pe.com.pacifico.helpia.config.RateLimitSettings;

/**
 * Contadores en Azure Table Storage con concurrencia optimista (ETag).
 * En Azure se usa el endpoint de tablas + Managed Identity (rol Storage Table Data Contributor).
 * En local se puede usar connection string de Azurite.
 */
public final class TableRateLimitStore implements RateLimitStore {

    private static final String COUNTER = "Contador";
    private static final String VALUE = "Valor";
    private static final int MAX_CONFLICT_RETRIES = 5;

    private final TableClient table;
    private volatile boolean tableReady;

    public TableRateLimitStore(TableClient table) {
        this.table = table;
    }

    public static TableRateLimitStore create(RateLimitSettings settings, TokenCredential credential) {
        TableClientBuilder builder = new TableClientBuilder().tableName(settings.tableName());
        if (!settings.connectionString().isEmpty()) {
            builder.connectionString(settings.connectionString());
        } else if (!settings.tableEndpoint().isEmpty()) {
            builder.endpoint(settings.tableEndpoint()).credential(credential);
        } else {
            throw new ApiException(ErrorCatalog.CONFIGURATION_ERROR);
        }
        return new TableRateLimitStore(builder.buildClient());
    }

    @Override
    public int incrementAndGet(String partitionKey, String rowKey) {
        ensureTable();
        for (int attempt = 0; attempt < MAX_CONFLICT_RETRIES; attempt++) {
            TableEntity current = find(partitionKey, rowKey);
            if (current == null) {
                try {
                    table.createEntity(new TableEntity(partitionKey, rowKey).addProperty(COUNTER, 1));
                    return 1;
                } catch (TableServiceException e) {
                    if (status(e) == 409) {
                        continue;
                    }
                    throw e;
                }
            }
            Object raw = current.getProperty(COUNTER);
            int next = (raw instanceof Number n ? n.intValue() : 0) + 1;
            current.addProperty(COUNTER, next);
            try {
                table.updateEntityWithResponse(current, TableEntityUpdateMode.REPLACE, true, null, Context.NONE);
                return next;
            } catch (TableServiceException e) {
                if (status(e) == 412) {
                    continue;
                }
                throw e;
            }
        }
        throw new IllegalStateException("No se pudo actualizar el contador por concurrencia");
    }

    @Override
    public Optional<Instant> getTimestamp(String partitionKey, String rowKey) {
        ensureTable();
        TableEntity entity = find(partitionKey, rowKey);
        if (entity == null || entity.getProperty(VALUE) == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse(String.valueOf(entity.getProperty(VALUE))));
    }

    @Override
    public void putTimestamp(String partitionKey, String rowKey, Instant value) {
        ensureTable();
        table.upsertEntity(new TableEntity(partitionKey, rowKey).addProperty(VALUE, value.toString()));
    }

    private TableEntity find(String partitionKey, String rowKey) {
        try {
            return table.getEntity(partitionKey, rowKey);
        } catch (TableServiceException e) {
            if (status(e) == 404) {
                return null;
            }
            throw e;
        }
    }

    private void ensureTable() {
        if (tableReady) {
            return;
        }
        try {
            table.createTable();
        } catch (TableServiceException e) {
            if (status(e) != 409) {
                throw e;
            }
        }
        tableReady = true;
    }

    private static int status(TableServiceException e) {
        return e.getResponse() == null ? -1 : e.getResponse().getStatusCode();
    }
}
