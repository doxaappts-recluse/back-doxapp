package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DisableMfaRequest(
        @NotBlank(message = "error.common.required") @Size(max = 128, message = "error.common.tooLong") String password,
        @NotBlank(message = "error.auth.mfaRequired") @Pattern(regexp = "\\d{6}", message = "error.auth.mfaInvalid") String code
) {
}
