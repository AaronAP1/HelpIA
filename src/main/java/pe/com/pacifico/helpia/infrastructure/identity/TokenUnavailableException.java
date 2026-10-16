package pe.com.pacifico.helpia.infrastructure.identity;

public class TokenUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
