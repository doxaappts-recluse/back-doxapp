package pe.dcs.app.features.user.org_admin_branch.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Agrega un acceso adicional (sede + rol) a una persona que ya
 * existe. Solo admite roles ORG_BRANCH_ADMIN/ORG_USER: ORG_ADMIN
 * es global y solo se asigna en la creación inicial de la persona.
 */
@Getter
@Setter
public class OrgAdminBranchAddAccessRequest {

    @NotNull(message = "{error.debeIndicarSede2}")
    private UUID branchId;

    @NotNull(message = "{error.elRolEsObligatorio}")
    private UUID roleId;
}
