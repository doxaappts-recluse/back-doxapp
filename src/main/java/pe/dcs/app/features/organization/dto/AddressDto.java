package pe.dcs.app.features.organization.dto;

import jakarta.validation.constraints.Size;

/** Dirección de la organización (todos los campos opcionales; el país es ISO-3166 alfa-2). */
public record AddressDto(
        @Size(max = 200, message = "error.common.tooLong") String line,
        @Size(max = 80, message = "error.common.tooLong") String district,
        @Size(max = 80, message = "error.common.tooLong") String city,
        @Size(max = 80, message = "error.common.tooLong") String region,
        @Size(max = 2, message = "error.common.tooLong") String country,
        @Size(max = 200, message = "error.common.tooLong") String reference
) {
}
