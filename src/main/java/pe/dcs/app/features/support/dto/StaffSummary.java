package pe.dcs.app.features.support.dto;

/** Contadores del tablero de plataforma. */
public record StaffSummary(long open, long waitingOrg, long waitingPlatform, long resolved, long unassigned, long mine, long breached) {
}
