package pe.dcs.app.features.auth.dto;

import java.util.UUID;

/** Un contexto de trabajo: un acceso (organización + sede + rol) o el propio personal de plataforma. */
public record ContextInfo(
        UUID id,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        UUID branchId,
        String branchName,
        String role
) {
}
