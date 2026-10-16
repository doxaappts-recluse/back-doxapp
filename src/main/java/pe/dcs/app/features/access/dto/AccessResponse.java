package pe.dcs.app.features.access.dto;

import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.shared.vo.DocumentType;
import pe.dcs.app.util.enums.RoleType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Acceso de una persona a la organización (vista del equipo: N2/N3). */
public record AccessResponse(
        UUID id,
        UUID personId,
        String fullName,
        DocumentType docType,
        String docNumber,
        String email,
        String phone,
        RoleType role,
        UUID branchId,
        String branchName,
        AccessStatus status,
        String statusReason,
        LocalDate validFrom,
        LocalDate validTo,
        boolean effective,
        boolean inviteAccepted,
        Instant lastLoginAt,
        List<PermissionItemDto> permissions,
        boolean self,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
