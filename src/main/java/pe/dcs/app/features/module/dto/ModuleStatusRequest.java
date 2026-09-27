package pe.dcs.app.features.module.dto;

import jakarta.validation.constraints.NotBlank;

/** status: PUBLISHED | RETIRED. */
public record ModuleStatusRequest(@NotBlank String status) {
}
