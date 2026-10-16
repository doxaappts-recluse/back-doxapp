package pe.dcs.app.features.contract.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;
import java.util.UUID;

/** Búsqueda de contratos (N1). q busca en nombre e identificador de la organización; expiringInDays = ACTIVE que vencen en N días. */
public record ContractSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    public record Filters(String q, UUID organizationId, String status, Integer expiringInDays) {
    }
}
