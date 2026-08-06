package pe.dcs.app.features.user.org_admin_branch.response;

import lombok.*;
import pe.dcs.app.util.auditable.AuditableResponse;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgAdminBranchResponse extends AuditableResponse {

    private UUID id;

    private String name;
    private String lastname;

    private String username;

    /**
     * Tiene credencial creada.
     */
    private Boolean hasCredential;

    /**
     * Estado de la credencial.
     * null cuando no tiene credencial.
     */
    private Boolean credentialActive;

    // Organización

    private UUID organizationId;
    private String organizationName;

    // Sede

    private UUID branchId;
    private String branchName;
    private Boolean branchMain;

    // Rol

    private UUID roleId;
    private String roleName;

}