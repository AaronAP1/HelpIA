package pe.com.pacifico.helpia.domain;

/** Referencia al job creado en Azure Automation. */
public record RunbookJob(String jobName, String correlationId, OperationType operacion) {
}
