package pe.dcs.app.features.access.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;
import java.util.UUID;

public record AccessSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    /** q busca en nombre, apellido, documento y correo de la persona. */
    public record Filters(String q, String role, String status, UUID branchId) {
    }
}
