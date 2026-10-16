package pe.dcs.app.features.support.dto;

/** status: RESOLVED | CLOSED | OPEN (reabrir un caso resuelto). note: texto opcional que queda visible en el hilo. */
public record CaseStatusRequest(String status, String note) {
}
