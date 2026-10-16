package pe.dcs.app.features.support.dto;

/** El personal puede corregir la prioridad (recalcula el SLA desde la apertura) y la categoría. */
public record CaseUpdateRequest(String priority, String category) {
}
