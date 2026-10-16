package pe.dcs.app.features.access.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.access.domain.AccessStatus;

/** ACTIVE (reactivar) o INACTIVE (con motivo). */
public record AccessStatusRequest(
        @NotNull(message = "error.common.required") AccessStatus status,
        @Size(max = 255, message = "error.common.tooLong") String reason
) {
}
