package pe.dcs.app.features.user_access.response;

import pe.dcs.app.util.enums.RoleType;

import java.util.UUID;

public record ContextBranchResponse(
        UUID organizationId,
        String organizationName,
        UUID branchId,
        String branchName,
        String branchCode,
        RoleType role
) {

}