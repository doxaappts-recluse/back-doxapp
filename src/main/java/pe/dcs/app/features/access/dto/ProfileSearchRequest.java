package pe.dcs.app.features.access.dto;

import pe.dcs.app.util.pagination.PaginationRequest;
import pe.dcs.app.util.pagination.SortRequest;

import java.util.List;

public record ProfileSearchRequest(Filters filters, PaginationRequest pagination, List<SortRequest> sorts) {

    public record Filters(String q, String status) {
    }
}
