package pe.dcs.app.features.organization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Alta de organización (N1). Nace en DRAFT: la activación es un paso aparte con su lista de requisitos. */
public record OrgCreateRequest(
        @NotBlank(message = "error.common.required") @Size(max = 120, message = "error.common.tooLong") String name,
        @Size(max = 160, message = "error.common.tooLong") String legalName,
        @Size(max = 11, message = "error.common.tooLong") String taxId,
        @NotBlank(message = "error.common.required") @Size(max = 30, message = "error.common.tooLong") String slug,
        @Size(max = 2, message = "error.common.tooLong") String country,
        @Size(max = 160, message = "error.common.tooLong") String email,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        LocalDate foundedDate,
        @Size(max = 50, message = "error.common.tooLong") String timezone,
        @Size(max = 3, message = "error.common.tooLong") String currency,
        @Size(max = 2, message = "error.common.tooLong") String defaultLanguage,
        @Min(value = 1, message = "error.common.invalid") @Max(value = 12, message = "error.common.invalid") Integer fiscalYearStartMonth,
        @Valid AddressDto address
) {
}
