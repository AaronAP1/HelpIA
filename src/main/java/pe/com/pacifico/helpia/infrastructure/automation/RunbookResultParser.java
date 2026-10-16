package pe.com.pacifico.helpia.infrastructure.automation;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Set;
import pe.com.pacifico.helpia.common.Json;
import pe.com.pacifico.helpia.domain.RunbookJobSnapshot;

/**
 * Traduce el estado de un job de Automation y la salida del runbook a un RunbookJobSnapshot.
 * Contrato: la última línea JSON de la salida debe traer {"resultado":"OK"|"ERROR","codigo":"...","mensaje":"..."}.
 */
public final class RunbookResultParser {

    public static final String COMPLETED = "Completed";
    private static final Set<String> TERMINAL_FAILURES = Set.of("failed", "stopped");

    private RunbookResultParser() {
    }

    public static boolean isCompleted(String automationStatus) {
        return COMPLETED.equalsIgnoreCase(automationStatus);
    }

    public static RunbookJobSnapshot toSnapshot(String automationStatus, String output) {
        String normalized = automationStatus == null ? "" : automationStatus.toLowerCase();

        if (isCompleted(automationStatus)) {
            JsonNode result = lastJsonObject(output);
            if (result == null) {
                return new RunbookJobSnapshot(automationStatus, true, false, "RUNBOOK_SIN_RESULTADO",
                        "El proceso terminó sin un resultado válido.");
            }
            boolean ok = "OK".equalsIgnoreCase(result.path("resultado").asText(""));
            String codigo = result.path("codigo").asText(ok ? "OK" : "ERROR_RUNBOOK");
            String mensaje = result.path("mensaje").asText(ok ? "Operación realizada." : "La operación no se pudo completar.");
            return new RunbookJobSnapshot(automationStatus, true, ok, codigo, mensaje);
        }

        if (TERMINAL_FAILURES.contains(normalized)) {
            return new RunbookJobSnapshot(automationStatus, true, false, "RUNBOOK_" + normalized.toUpperCase(),
                    "La operación no se pudo completar.");
        }

        // New, Activating, Running, Queued, Suspended, Blocked, Disconnected...: se sigue esperando.
        return new RunbookJobSnapshot(automationStatus, false, false, null, null);
    }

    static JsonNode lastJsonObject(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        String[] lines = output.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].strip();
            if (line.startsWith("{") && line.endsWith("}")) {
                try {
                    JsonNode node = Json.MAPPER.readTree(line);
                    if (node != null && node.isObject() && node.has("resultado")) {
                        return node;
                    }
                } catch (IOException ignored) {
                    // Línea que parece JSON pero no lo es: se sigue buscando hacia arriba.
                }
            }
        }
        return null;
    }
}
