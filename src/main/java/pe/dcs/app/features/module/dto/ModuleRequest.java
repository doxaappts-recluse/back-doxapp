package pe.dcs.app.features.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Alta/edición de un módulo del catálogo. El código solo se envía al crear (es inmutable). */
public record ModuleRequest(
        String code,
        @NotBlank @Size(max = 100) String nameEs,
        @NotBlank @Size(max = 100) String nameEn,
        List<String> levels,
        String parentCode,
        String kind,
        List<String> actions,
        Boolean delegable,
        @Size(max = 120) String route,
        @Size(max = 60) String icon,
        Integer sortOrder
) {
}
