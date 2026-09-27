package pe.dcs.app.features.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Un renglón de la matriz módulo × acciones (delegación y perfiles). */
public record PermissionItemDto(
        @NotBlank(message = "error.common.required") @Size(max = 40, message = "error.common.tooLong") String moduleCode,
        List<String> actions
) {
}
