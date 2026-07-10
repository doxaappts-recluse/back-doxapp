package pe.dcs.app.security.service;

import pe.dcs.app.util.constant.RoleConstant;

import java.util.UUID;

public record UserAccessContext(
        UUID organizationId,
        UUID branchId,
        String roleCode
) {

    public boolean isSystem(){
        return RoleConstant.isSystem(roleCode);
    }

    public boolean isOrgAdmin(){
        return RoleConstant.ORG_ADMIN.equals(roleCode);
    }

    public boolean isBranchUser(){
        return branchId != null;
    }

}