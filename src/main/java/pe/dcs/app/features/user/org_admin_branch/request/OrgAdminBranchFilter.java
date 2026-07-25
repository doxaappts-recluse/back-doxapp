package pe.dcs.app.features.user.org_admin_branch.request;

import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Getter
@Setter
public class OrgAdminBranchFilter {

    /**
     * Solo lo usa SYSTEM_ADMIN.
     * ORG_ADMIN no lo enviará.
     */
    private UUID organizationId;

    /**
     * Opcional.
     */
    private UUID branchId;

    /**
     * Buscar por nombre, apellido,
     * DNI o username.
     */
    private String name;

    private String lastname;

    private String dni;

    private String username;

    private Boolean hasCredential;
    private Boolean credentialActive;

    /**
     * ORG_ADMIN / ORG_BRANCH_ADMIN
     */
    private RoleType role;

    /**
     * Estado de la credencial.
     */
    private StatusType status;

}