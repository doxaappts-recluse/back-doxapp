package pe.dcs.app.features.plan.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

/** Búsqueda de planes. q busca en código y nombre. */
public record PlanSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    public record Filters(String q, String status) {
    }
}
