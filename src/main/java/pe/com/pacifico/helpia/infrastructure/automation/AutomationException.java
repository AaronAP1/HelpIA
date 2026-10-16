package pe.com.pacifico.helpia.infrastructure.automation;

/** Falla al hablar con Azure Automation. Dentro de una activity provoca el reintento de Durable. */
public class AutomationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AutomationException(String message) {
        super(message);
    }

    public AutomationException(String message, Throwable cause) {
        super(message, cause);
    }
}
