package features.branch.dto;

import features.organization.dto.AddressDto;

import java.util.List;

/** Lo único que se publica de una sede (sin sesión): nombre, dirección, horarios y contacto público. */
public record PublicBranchResponse(
        String name,
        AddressDto address,
        String phone,
        String email,
        String logoUrl,
        List<ScheduleEntryDto> schedule
) {
}
