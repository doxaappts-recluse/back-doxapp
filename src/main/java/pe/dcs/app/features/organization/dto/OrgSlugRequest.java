package pe.dcs.app.features.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrgSlugRequest(
        @NotBlank(message = "error.common.required") @Size(max = 30, message = "error.common.tooLong") String slug
) {
}
