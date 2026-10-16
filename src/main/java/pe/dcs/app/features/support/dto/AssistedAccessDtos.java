package pe.dcs.app.features.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** M22 (D3) · DTOs del ciclo de vida del acceso asistido. */
public class AssistedAccessDtos {

    /** Staff pide acceso desde un caso abierto: alcance (códigos de módulo de configuración) + motivo + duración deseada (min, ≤240). */
    public record RequestAccess(List<String> scope, String reason, Integer durationMinutes) {
    }

    /** ORG_ADMIN aprueba; duración opcional (si no se manda, se usa la pedida por el staff, tope 240 min). */
    public record ApproveRequest(Integer durationMinutes) {
    }

    public record DenyRequest(String reason) {
    }

    /** Vista común (staff y org) de un grant. */
    public record GrantResponse(
            UUID id,
            UUID organizationId,
            String organizationName,
            UUID caseId,
            long caseNumber,
            UUID staffId,
            String staffName,
            List<String> scope,
            String reason,
            Instant requestedAt,
            UUID approvedBy,
            String approvedByName,
            String deniedReason,
            Instant startsAt,
            Instant expiresAt,
            Instant revokedAt,
            String status
    ) {
    }

    /** Resultado de "entrar" al modo asistido: token propio (sin refresh — dura hasta que expire o se revoque el grant). */
    public record EnterResponse(String token, Instant expiresAt, UUID organizationId, String organizationName, List<String> scope) {
    }
}
