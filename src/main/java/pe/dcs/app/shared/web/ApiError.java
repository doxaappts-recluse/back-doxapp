package pe.dcs.app.shared.web;

import java.time.Instant;
import java.util.List;

/**
 * Spec 00 §10 · cuerpo de error unificado: {status, code, message, fieldErrors, traceId}.
 * 400 validación de campo · 401 · 403 permiso/contrato · 404 inexistente o fuera de alcance · 409 estado/duplicado · 422 regla de negocio.
 */
public record ApiError(
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors,
        String traceId,
        Instant timestamp
) {
    public record FieldError(String field, String code, String message) {
    }
}
