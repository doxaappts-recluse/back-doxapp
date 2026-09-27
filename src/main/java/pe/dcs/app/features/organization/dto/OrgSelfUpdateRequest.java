package pe.dcs.app.features.organization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * "Mi organización" (ORG_ADMIN): solo contacto, dirección, fecha de fundación, idioma y mes de inicio del año fiscal.
 * Nombre, razón social, RUC, slug, zona horaria y moneda los cambia únicamente la plataforma (N1).
 */
public record OrgSelfUpdateRequest(
        @Size(max = 160, message = "error.common.tooLong") String email,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        LocalDate foundedDate,
        @NotBlank(message = "error.common.required") @Size(max = 2, message = "error.common.tooLong") String defaultLanguage,
        @Min(value = 1, message = "error.common.invalid") @Max(value = 12, message = "error.common.invalid") Integer fiscalYearStartMonth,
        @Valid AddressDto address
) {
}
