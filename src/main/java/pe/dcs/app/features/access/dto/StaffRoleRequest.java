package pe.dcs.app.features.access.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.access.domain.StaffRole;

public record StaffRoleRequest(
        @NotNull(message = "error.common.required") StaffRole staffRole,
        @Size(max = 255, message = "error.common.tooLong") String reason
) {
}
