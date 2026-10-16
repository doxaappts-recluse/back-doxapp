package pe.dcs.app.features.contract.dto;

/** Resultado de una corrida del job de contratos: iniciados (renovaciones que llegaron a su fecha), vencidos y avisos enviados. */
public record MaintenanceResponse(int started, int expired, int notices) {
}
