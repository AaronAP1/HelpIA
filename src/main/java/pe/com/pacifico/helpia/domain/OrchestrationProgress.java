package pe.com.pacifico.helpia.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Custom status de la orquestación. El starter lo consulta durante la espera corta:
 * si la fase es RESULTADO_LISTO responde 200 con el resultado; si no, responde 202.
 * Durable lo serializa con Jackson: isReady() no debe viajar como propiedad "ready", porque al leerlo
 * Jackson fallaba por campo desconocido y el starter nunca veía el resultado (siempre 202).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrchestrationProgress(String fase, OperationResult resultado) {

    public static final String EJECUTANDO = "EJECUTANDO";
    public static final String RESULTADO_LISTO = "RESULTADO_LISTO";

    public static OrchestrationProgress running() {
        return new OrchestrationProgress(EJECUTANDO, null);
    }

    public static OrchestrationProgress ready(OperationResult result) {
        return new OrchestrationProgress(RESULTADO_LISTO, result);
    }

    @JsonIgnore
    public boolean isReady() {
        return RESULTADO_LISTO.equals(fase) && resultado != null;
    }
}
