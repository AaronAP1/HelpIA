package pe.com.pacifico.helpia.common;

import java.util.Map;

/** Lectura de headers sin depender de mayúsculas/minúsculas. */
public final class Headers {

    private Headers() {
    }

    public static String get(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
