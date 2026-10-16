package pe.com.pacifico.helpia.infrastructure.callback;

/** El callback no fue aceptado. Dentro de la activity provoca el reintento de Durable. */
public class CallbackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CallbackException(String message) {
        super(message);
    }

    public CallbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
