package pe.dcs.app.features.user.org_admin_branch.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Un acceso puntual (persona + organización + sede opcional + rol)
 * de una persona. Una persona puede tener varios, salvo que uno
 * de ellos sea ORG_ADMIN (global, excluyente).
 */
@Getter
@Setter
public class UserAccessResponse {

    private UUID id;

    private UUID organizationId;
    private String organizationName;

    private UUID branchId;
    private String branchName;

    private UUID roleId;
    private String roleName;
    private String roleCode;

    private boolean active;
}
