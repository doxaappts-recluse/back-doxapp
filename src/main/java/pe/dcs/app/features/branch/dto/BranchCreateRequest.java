package pe.dcs.app.features.branch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.organization.dto.AddressDto;

import java.time.LocalDate;
import java.util.List;

/** Alta de sede por N1. La primera sede de la organización queda como principal automáticamente. */
public record BranchCreateRequest(
        @NotBlank(message = "error.common.required") @Size(max = 100, message = "error.common.tooLong") String name,
        @NotBlank(message = "error.common.required") @Size(max = 10, message = "error.common.tooLong") String code,
        @Size(max = 60, message = "error.common.tooLong") String displayName,
        @Valid AddressDto address,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        @Size(max = 160, message = "error.common.tooLong") String email,
        LocalDate openingDate,
        @Size(max = 50, message = "error.common.tooLong") String timezone,
        @Valid List<ScheduleEntryDto> publicSchedule
) {
}
