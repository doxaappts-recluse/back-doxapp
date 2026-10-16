package pe.dcs.app.features.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank(message = "error.common.required") String refreshToken) {
}
