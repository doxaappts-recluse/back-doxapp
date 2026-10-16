package pe.dcs.app.features.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corregir el correo de un administrador cuya invitación aún no se aceptó. */
public record OrgAdminEmailRequest(@NotBlank(message = "error.common.required") @Size(max = 160, message = "error.common.tooLong") String email) {
}
