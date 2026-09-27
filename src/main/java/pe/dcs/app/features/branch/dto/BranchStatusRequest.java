package pe.dcs.app.features.branch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Inactivar / reactivar una sede. El motivo es obligatorio al inactivar. Si la sede es la principal,
 * {@code newMainBranchId} debe nombrar otra sede ACTIVA que pasa a ser la principal [V5].
 */
public record BranchStatusRequest(
        @NotNull(message = "error.common.required") StatusType status,
        @Size(max = 255, message = "error.common.tooLong") String reason,
        UUID newMainBranchId
) {
}
