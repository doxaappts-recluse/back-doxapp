package pe.dcs.app.features.organization.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/** Datos de marca (los archivos se suben aparte). Vacío/nulo = usar el valor por defecto. */
public record BrandingUpdateRequest(
        @Size(max = 60, message = "error.common.tooLong") String displayName,
        @Size(max = 7, message = "error.common.tooLong") String primaryColor,
        @Size(max = 7, message = "error.common.tooLong") String secondaryColor,
        @Size(max = 300, message = "error.common.tooLong") String welcomeTextEs,
        @Size(max = 300, message = "error.common.tooLong") String welcomeTextEn,
        @Size(max = 160, message = "error.common.tooLong") String contactEmail,
        @Size(max = 20, message = "error.common.tooLong") String contactPhone,
        Map<String, String> socials
) {
}
