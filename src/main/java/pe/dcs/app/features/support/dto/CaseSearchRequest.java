package pe.dcs.app.features.support.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

import java.util.List;
import java.util.UUID;

/**
 * Búsqueda de casos. q busca en asunto, código (CS-123 o 123) y quien lo abrió. assignee: ME | UNASSIGNED | id de personal.
 * scope (solo organización): ALL | MINE. breached (solo plataforma): casos con SLA vencido.
 */
public record CaseSearchRequest(Filters filters, PaginationRequest pagination) {

    public record Filters(String q, List<String> status, String priority, String category, String assignee, UUID organizationId,
                          UUID branchId, Boolean breached, Boolean activeOnly) {
    }
}
