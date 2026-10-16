package pe.com.pacifico.helpia.domain;

/** Estado de un job de Automation, tal como lo devuelve la activity de consulta. */
public record RunbookJobSnapshot(String automationStatus, boolean terminal, boolean exitoso, String codigo, String mensaje) {
}
