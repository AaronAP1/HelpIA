package pe.com.pacifico.helpia.application;

import pe.com.pacifico.helpia.domain.OperationType;

/** Datos validados de una solicitud de desbloqueo, reseteo o cambio de celular. */
public record OperationRequest(OperationType type, String usuario, String dominio, String celular, String canal) {
}
