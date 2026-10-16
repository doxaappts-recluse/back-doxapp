package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaCodeRequest(
        @NotBlank(message = "error.auth.mfaRequired") @Pattern(regexp = "\\d{6}", message = "error.auth.mfaInvalid") String code
) {
}
