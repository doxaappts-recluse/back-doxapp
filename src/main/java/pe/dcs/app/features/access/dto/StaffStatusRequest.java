package pe.dcs.app.features.access.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.access.domain.AccessStatus;

/** Solo se piden ACTIVE (reactivar) o INACTIVE (con motivo). INVITED y LOCKED no se fijan a mano. */
public record StaffStatusRequest(
        @NotNull(message = "error.common.required") AccessStatus status,
        @Size(max = 255, message = "error.common.tooLong") String reason
) {
}
