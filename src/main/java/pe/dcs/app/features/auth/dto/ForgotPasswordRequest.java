package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code organizationSlug} nulo = personal de plataforma. */
public record ForgotPasswordRequest(
        @Size(max = 30, message = "error.common.tooLong") String organizationSlug,
        @NotBlank(message = "error.common.required") @Size(max = 160, message = "error.common.tooLong") String username
) {
}
