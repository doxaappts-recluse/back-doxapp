package pe.dcs.app.features.organization.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

/** Búsqueda paginada de organizaciones (N1). q busca en nombre, razón social, RUC y slug. */
public record OrgSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    public record Filters(String q, String status, String country) {
    }
}
