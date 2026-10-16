package pe.dcs.app.security.authz;

import pe.dcs.app.util.enums.RoleType;

import java.util.Set;
import java.util.UUID;

/**
 * Alcance de datos de la petición (spec 00 §6.5): organización + sedes visibles + persona autenticada.
 * Todo repositorio de tenant recibe este alcance (vía {@link TenantSpecs}); prohibido consultar por id sin él.
 */
public record AccessScope(
        UUID organizationId,
        boolean allBranches,
        Set<UUID> branchIds,
        UUID personId,
        UUID accessId,
        RoleType role
) {
    public boolean canSeeBranch(UUID branchId) {
        return allBranches || (branchId != null && branchIds.contains(branchId));
    }
}
