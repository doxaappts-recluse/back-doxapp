package pe.dcs.app.features.organization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Edición completa por N1 (el slug tiene su propio endpoint porque genera redirección). */
public record OrgUpdateRequest(
        @NotBlank(message = "error.common.required") @Size(max = 120, message = "error.common.tooLong") String name,
        @Size(max = 160, message = "error.common.tooLong") String legalName,
        @Size(max = 11, message = "error.common.tooLong") String taxId,
        @Size(max = 2, message = "error.common.tooLong") String country,
        @Size(max = 160, message = "error.common.tooLong") String email,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        LocalDate foundedDate,
        @NotBlank(message = "error.common.required") @Size(max = 50, message = "error.common.tooLong") String timezone,
        @NotBlank(message = "error.common.required") @Size(max = 3, message = "error.common.tooLong") String currency,
        @NotBlank(message = "error.common.required") @Size(max = 2, message = "error.common.tooLong") String defaultLanguage,
        @Min(value = 1, message = "error.common.invalid") @Max(value = 12, message = "error.common.invalid") Integer fiscalYearStartMonth,
        @Valid AddressDto address
) {
}
