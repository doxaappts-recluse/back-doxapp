package pe.dcs.app.features.plan.dto;

import jakarta.validation.constraints.NotBlank;

/** status: PUBLISHED | RETIRED. */
public record PlanStatusRequest(@NotBlank String status) {
}
