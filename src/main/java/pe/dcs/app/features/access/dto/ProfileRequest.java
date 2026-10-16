package pe.dcs.app.features.access.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileRequest(
        @NotBlank(message = "error.common.required") @Size(max = 80, message = "error.common.tooLong") String name,
        @Size(max = 255, message = "error.common.tooLong") String description,
        @Valid List<PermissionItemDto> items,
        Long version
) {
}
