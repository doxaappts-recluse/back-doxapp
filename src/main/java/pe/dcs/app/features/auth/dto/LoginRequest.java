package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code organizationSlug} solo lo usa el login directo (/auth/login) cuando el mismo usuario existe en varias
 * organizaciones y la persona ya eligió una ("@platform" = personal de plataforma). Los ingresos por puerta lo ignoran.
 */
public record LoginRequest(
        @NotBlank(message = "error.common.required") @Size(max = 160, message = "error.common.tooLong") String username,
        @NotBlank(message = "error.common.required") @Size(max = 128, message = "error.common.tooLong") String password,
        @Size(max = 30, message = "error.common.tooLong") String organizationSlug
) {
}
