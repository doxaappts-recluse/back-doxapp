package pe.dcs.app.features.branch.dto;

import pe.dcs.app.features.organization.dto.AddressDto;
import pe.dcs.app.util.enums.StatusType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sede completa. {@code canEdit}/{@code canEditIdentity} indican qué puede cambiar quien consulta. Los contadores
 * ({@code activePeople}, {@code activeContracts}) solo se llenan para N1 (explican por qué no se puede inactivar).
 */
public record BranchResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        String name,
        String code,
        boolean main,
        StatusType status,
        String statusReason,
        Instant inactivatedAt,
        String displayName,
        String effectiveName,
        AddressDto address,
        String phone,
        String email,
        LocalDate openingDate,
        String timezone,
        String effectiveTimezone,
        String logoUrl,
        List<ScheduleEntryDto> publicSchedule,
        boolean canEdit,
        boolean canEditIdentity,
        Long activePeople,
        Long activeContracts,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
