package pe.com.pacifico.helpia.domain;

/**
 * Evento externo que el starter envía a la orquestación para indicar cómo entregó la respuesta.
 * SINCRONO: el bot ya recibió el resultado en el 200, no se envía callback.
 * ASINCRONO (o sin evento dentro del plazo): se envía callback.
 */
public final class DeliveryMode {

    public static final String EVENT_NAME = "ModoEntrega";
    public static final String SINCRONO = "SINCRONO";
    public static final String ASINCRONO = "ASINCRONO";

    private DeliveryMode() {
    }
}
