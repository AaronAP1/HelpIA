package pe.com.pacifico.helpia.function;

import java.util.function.Supplier;

/**
 * Inicialización perezosa y thread-safe. Solo memoriza el éxito: si la configuración falla,
 * cada request devuelve el error (igual que Python, que validaba la configuración en cada llamada).
 */
public final class Lazy<T> implements Supplier<T> {

    private final Supplier<T> factory;
    private volatile T value;

    public Lazy(Supplier<T> factory) {
        this.factory = factory;
    }

    @Override
    public T get() {
        T current = value;
        if (current == null) {
            synchronized (this) {
                current = value;
                if (current == null) {
                    current = factory.get();
                    value = current;
                }
            }
        }
        return current;
    }
}
