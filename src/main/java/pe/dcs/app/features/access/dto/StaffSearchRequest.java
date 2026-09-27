package pe.dcs.app.features.access.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

/** Búsqueda paginada de personal de plataforma: {filters, pagination, sorts} (mismo contrato que el resto de listados). */
public record StaffSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    /** q busca en nombre, apellido, correo y documento. */
    public record Filters(String q, String staffRole, String status) {
    }
}
