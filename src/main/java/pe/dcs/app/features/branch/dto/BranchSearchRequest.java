package pe.dcs.app.features.branch.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

/** Búsqueda paginada de sedes. q busca en nombre, nombre visible, código y ciudad. */
public record BranchSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    public record Filters(String q, String status) {
    }
}
