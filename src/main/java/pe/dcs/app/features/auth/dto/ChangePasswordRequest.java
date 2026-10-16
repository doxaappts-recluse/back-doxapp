package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "error.common.required") @Size(max = 128, message = "error.common.tooLong") String currentPassword,
        @NotBlank(message = "error.common.required") @Size(max = 128, message = "error.common.tooLong") String newPassword
) {
}
