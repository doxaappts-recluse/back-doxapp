package pe.dcs.app.features.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.access.domain.StaffRole;
import pe.dcs.app.shared.vo.DocumentType;

import java.time.LocalDate;

public record StaffCreateRequest(
        @NotBlank(message = "error.common.required") @Size(max = 80, message = "error.common.tooLong") String firstName,
        @NotBlank(message = "error.common.required") @Size(max = 80, message = "error.common.tooLong") String lastName,
        @NotNull(message = "error.common.required") DocumentType docType,
        @NotBlank(message = "error.common.required") @Size(max = 12, message = "error.common.tooLong") String docNumber,
        @NotBlank(message = "error.common.required") @Size(max = 160, message = "error.common.tooLong") String email,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        @Size(max = 80, message = "error.common.tooLong") String position,
        @NotNull(message = "error.common.required") StaffRole staffRole,
        LocalDate hireDate
) {
}
