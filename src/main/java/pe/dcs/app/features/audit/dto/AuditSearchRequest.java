package pe.dcs.app.features.audit.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Búsqueda de eventos de auditoría (M22). Sin fechas se consultan los últimos 30 días; el rango no puede superar 1 año.
 * organizationId solo lo usa la plataforma (para ver lo que su personal hizo sobre una organización).
 */
public record AuditSearchRequest(Filters filters, PaginationRequest pagination) {

    public record Filters(UUID actorId, String moduleCode, String action, String entityType, String entityId,
                          UUID branchId, UUID organizationId, Instant from, Instant to) {
    }
}
