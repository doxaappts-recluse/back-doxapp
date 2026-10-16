package pe.dcs.app.features.access.dto;

import pe.dcs.app.features.access.domain.AccessStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Administrador de una organización visto desde la plataforma (N1): solo lo necesario para gestionar el acceso.
 * SYSTEM_SUPPORT recibe nombre, estado y último ingreso ({@code email} y {@code statusReason} vienen nulos).
 * Nunca se devuelve documento, teléfono ni datos de la ficha de la persona (regla D3).
 */
public record OrgAdminResponse(
        UUID id,
        String fullName,
        String email,
        AccessStatus status,
        String statusReason,
        boolean inviteAccepted,
        Instant lastLoginAt,
        Instant createdAt
) {
}
