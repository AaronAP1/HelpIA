package pe.com.pacifico.helpia.domain;

import java.time.Instant;

/** Resultado final de una operación. Es lo que recibe el bot (respuesta 200 o callback). */
public record OperationResult(
        String correlationId,
        OperationType operacion,
        OperationStatus estado,
        String codigo,
        String mensaje,
        String finalizadoEn,
        Boolean callbackEntregado) {

    public static OperationResult of(OperationCommand cmd, OperationStatus estado, String codigo, String mensaje, Instant at) {
        return new OperationResult(cmd.correlationId(), cmd.operacion(), estado, codigo, mensaje, at.toString(), null);
    }

    public OperationResult withCallbackDelivered(boolean delivered) {
        return new OperationResult(correlationId, operacion, estado, codigo, mensaje, finalizadoEn, delivered);
    }
}
