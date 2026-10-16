package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
        @NotBlank(message = "error.common.required") String token,
        @NotBlank(message = "error.common.required") @Size(max = 128, message = "error.common.tooLong") String newPassword
) {
}
