package pe.dcs.app.features.branch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import pe.dcs.app.features.organization.dto.AddressDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Edición de N2/N3: solo la presentación (nombre visible, contacto, dirección, horarios). Los campos de identidad
 * ({@code name, code, main, status, openingDate, timezone}) existen únicamente para poder rechazarlos con 403 si
 * llegan con un valor distinto al vigente [V9]; el logo se sube por su propio endpoint.
 */
public record BranchSelfUpdateRequest(
        @Size(max = 60, message = "error.common.tooLong") String displayName,
        @Valid AddressDto address,
        @Size(max = 20, message = "error.common.tooLong") String phone,
        @Size(max = 160, message = "error.common.tooLong") String email,
        @Valid List<ScheduleEntryDto> publicSchedule,
        Long version,
        String name,
        String code,
        Boolean main,
        String status,
        LocalDate openingDate,
        String timezone
) {
}
