package pe.com.pacifico.helpia.common;

/**
 * Error controlado de la API. Reemplaza a AuthenticationError, RouterError y UserFindError de Python,
 * que eran tres clases idénticas.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCatalog error;

    public ApiException(ErrorCatalog error) {
        super(error.defaultMessage());
        this.error = error;
    }

    public ApiException(ErrorCatalog error, Throwable cause) {
        super(error.defaultMessage(), cause);
        this.error = error;
    }

    public ErrorCatalog error() {
        return error;
    }

    public String code() {
        return error.name();
    }

    public int httpStatus() {
        return error.httpStatus();
    }
}
