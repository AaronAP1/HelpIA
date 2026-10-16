package pe.com.pacifico.helpia.common;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lectura tipada de app settings. Recibe la fuente para poder probarla sin variables reales. */
public final class Env {

    private final Function<String, String> source;

    public Env(Function<String, String> source) {
        this.source = source;
    }

    public static Env system() {
        return new Env(System::getenv);
    }

    public String get(String name) {
        String value = source.apply(name);
        return value == null ? "" : value.trim();
    }

    public String get(String name, String defaultValue) {
        String value = get(name);
        return value.isEmpty() ? defaultValue : value;
    }

    public boolean isTrue(String name) {
        return Set.of("1", "true", "yes", "on").contains(get(name).toLowerCase(Locale.ROOT));
    }

    public int getInt(String name, int defaultValue) {
        String value = get(name);
        if (value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Lista separada por comas, sin vacíos. */
    public Set<String> getList(String name, boolean lowerCase) {
        return Arrays.stream(get(name).split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> lowerCase ? v.toLowerCase(Locale.ROOT) : v)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Mismo criterio que Python: WEBSITE_INSTANCE_ID o WEBSITE_SITE_NAME presentes. */
    public boolean isRunningInAzure() {
        return !get("WEBSITE_INSTANCE_ID").isEmpty() || !get("WEBSITE_SITE_NAME").isEmpty();
    }
}
