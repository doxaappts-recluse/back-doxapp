package pe.dcs.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.constant.RoleConstant;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    public boolean isSystem(CredentialDetailsImpl user){
        return user.isSystem();
    }

    public boolean hasRole(
            CredentialDetailsImpl user,
            String role
    ){
        return user.hasRole(role);
    }

    public boolean isOrgAdmin(
            CredentialDetailsImpl user,
            UUID organizationId
    ){

        return user.hasOrganizationAdminAccess(
                organizationId
        );
    }

    public boolean isBranchAdmin(
            CredentialDetailsImpl user,
            UUID organizationId,
            UUID branchId
    ){

        return user.hasBranchAdminAccess(
                organizationId,
                branchId
        );
    }

    public boolean isOrgBranchAdmin(
            CredentialDetailsImpl user,
            UUID organizationId,
            UUID branchId
    ){

        return user.hasBranchAdminAccess(
                organizationId,
                branchId
        );
    }

    public boolean isOrgUser(
            CredentialDetailsImpl user,
            UUID organizationId,
            UUID branchId
    ){
        return user.hasOrganizationUser(
                organizationId,
                branchId
        );
    }

    public boolean hasOrganizationAccess(
            CredentialDetailsImpl user,
            UUID organizationId
    ){

        if(isSystem(user)){
            return true;
        }

        return user.hasOrganization(
                organizationId
        );
    }

    public boolean hasBranchAccess(
            CredentialDetailsImpl user,
            UUID organizationId,
            UUID branchId
    ){

        if(isSystem(user)){
            return true;
        }

        if(user.hasOrganizationAdminAccess(organizationId)){
            return true;
        }

        return user.hasBranch(
                organizationId,
                branchId
        );
    }

    public void assertCanAccessOrganization(
            CredentialDetailsImpl actor,
            UUID organizationId
    ){

        if(!hasOrganizationAccess(
                actor,
                organizationId
        )){
            throw new Exceptions(
                    "Organization access denied",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public void assertCanAccessBranch(
            CredentialDetailsImpl actor,
            UUID organizationId,
            UUID branchId
    ){

        if(!hasBranchAccess(
                actor,
                organizationId,
                branchId
        )){
            throw new Exceptions(
                    "Branch access denied",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public void assertCanManageUser(
            CredentialDetailsImpl actor,
            UUID targetOrganizationId,
            UUID targetBranchId
    ){

        if(isSystem(actor)){
            return;
        }

        if(!hasOrganizationAccess(
                actor,
                targetOrganizationId
        )){
            throw new Exceptions(
                    "Different organization",
                    HttpStatus.FORBIDDEN
            );
        }

        if(actor.hasOrganizationAdminAccess(
                targetOrganizationId
        )){
            return;
        }

        if(!hasBranchAccess(
                actor,
                targetOrganizationId,
                targetBranchId
        )){
            throw new Exceptions(
                    "Different branch",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}