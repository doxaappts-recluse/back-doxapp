package pe.dcs.app.features.contract.dto;

import jakarta.validation.constraints.Size;

/** Suspender y cancelar exigen motivo; activar lo ignora. */
public record ContractActionRequest(@Size(max = 255) String reason, Long version) {
}
