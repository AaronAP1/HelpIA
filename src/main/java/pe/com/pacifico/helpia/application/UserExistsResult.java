package pe.com.pacifico.helpia.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Respuesta de users/exists. Conserva exactamente los campos y el orden del servicio Python
 * (status, email, exists, emailMatches, matchedBy, matricula, dominioLocal) para no romper al consumidor.
 * Se agrega cuentaHabilitada, que es un campo nuevo y aditivo.
 */
public record UserExistsResult(
        String email,
        boolean exists,
        String matchedBy,
        String matricula,
        String dominioLocal,
        Boolean cuentaHabilitada) {

    public static UserExistsResult notFound(String email) {
        return new UserExistsResult(email, false, null, null, null, null);
    }

    public Map<String, Object> toResponseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        body.put("email", email);
        body.put("exists", exists);
        body.put("emailMatches", exists && matchedBy != null);
        body.put("matchedBy", matchedBy);
        body.put("matricula", matricula);
        body.put("dominioLocal", dominioLocal);
        body.put("cuentaHabilitada", cuentaHabilitada);
        return body;
    }
}
