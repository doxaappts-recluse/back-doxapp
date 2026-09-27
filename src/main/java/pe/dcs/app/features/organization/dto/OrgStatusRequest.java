package pe.dcs.app.features.organization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.organization.domain.OrganizationStatus;

import java.time.LocalDate;

/**
 * Cambio de estado. reason es obligatorio al SUSPENDER; trialEndsOn solo aplica a TRIAL (por defecto +30 días);
 * confirmSlug es obligatorio al CERRAR y debe ser igual al slug de la organización.
 */
public record OrgStatusRequest(
        @NotNull(message = "error.common.required") OrganizationStatus status,
        @Size(max = 255, message = "error.common.tooLong") String reason,
        LocalDate trialEndsOn,
        @Size(max = 30, message = "error.common.tooLong") String confirmSlug
) {
}
