package features.access.dto;

import features.access.domain.AccessStatus;
import features.access.domain.StaffRole;
import pe.dcs.app.shared.vo.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Fila/detalle del personal de plataforma. {@code self} = es el propio usuario (el front oculta cambio de rol/estado). */
public record StaffResponse(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        DocumentType docType,
        String docNumber,
        String email,
        String phone,
        String position,
        StaffRole staffRole,
        AccessStatus status,
        String statusReason,
        LocalDate hireDate,
        Instant lastLoginAt,
        boolean mfaEnabled,
        boolean locked,
        boolean self,
        Instant createdAt,
        Instant updatedAt
) {
}
