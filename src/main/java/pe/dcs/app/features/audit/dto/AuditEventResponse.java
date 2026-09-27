package pe.dcs.app.features.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Evento de auditoría. diff, ip y userAgent solo viajan en el detalle; masked indica que se ocultaron campos sensibles (V14). */
public record AuditEventResponse(
        Long id,
        Instant at,
        String actorType,
        UUID actorId,
        String actorName,
        String actorRole,
        UUID organizationId,
        String organizationName,
        UUID branchId,
        String branchName,
        String moduleCode,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> diff,
        boolean masked,
        String ip,
        String userAgent,
        UUID assistedGrantId
) {
}
