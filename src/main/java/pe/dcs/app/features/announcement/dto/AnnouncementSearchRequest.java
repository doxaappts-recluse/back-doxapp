package pe.dcs.app.features.announcement.dto;

import pe.dcs.app.util.pagination.PaginationRequest;

public record AnnouncementSearchRequest(Filters filters, PaginationRequest pagination) {

    /** phase: SCHEDULED | ACTIVE | ENDED (según las fechas, para los publicados). */
    public record Filters(String q, String status, String severity, String phase) {
    }
}
