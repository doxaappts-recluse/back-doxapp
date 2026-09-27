package pe.dcs.app.features.plan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Alta/edición de plan. El código solo se envía al crear (es inmutable). */
public record PlanRequest(
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency,
        List<String> modules
) {
}
