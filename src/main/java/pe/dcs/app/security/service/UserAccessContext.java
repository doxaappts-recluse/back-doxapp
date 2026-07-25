package pe.dcs.app.security.service;

import pe.dcs.app.util.enums.RoleType;

import java.util.UUID;

public record UserAccessContext(
        UUID organizationId,
        UUID branchId,
        RoleType roleCode
) {

    public boolean isSystem(){
        return RoleType.SYSTEM_ADMIN.equals(roleCode) || RoleType.SYSTEM_SUPPORT.equals((roleCode));
    }

    public boolean isOrgAdmin(){
        return RoleType.ORG_ADMIN.equals(roleCode);
    }

    public boolean isBranchUser(){
        return branchId != null;
    }

}