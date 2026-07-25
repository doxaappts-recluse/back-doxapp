package pe.dcs.app.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AuthContext {

    private final OrganizationContext organizationContext;

    public CredentialDetailsImpl getPrincipal(){

        Object principal =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        if(!(principal instanceof CredentialDetailsImpl)){
            throw new IllegalStateException(
                    "No authenticated user"
            );
        }

        return (CredentialDetailsImpl) principal;
    }

    public UUID getUserId(){
        return getPrincipal().getUserId();
    }

    public List<UserAccessContext> getAccesses(){
        return getPrincipal().getAccesses();
    }

    public boolean isSystem(){
        return getPrincipal().isSystemAdmin() || getPrincipal().isSystemSupport();
    }

    public boolean hasRole(String role){
        return getPrincipal().hasRole(role);
    }

    public boolean hasOrganizationAccess(UUID organizationId){
        return getPrincipal()
                .hasOrganization(
                        organizationId
                );
    }

    public boolean hasBranchAccess(UUID organizationId, UUID branchId){
        return getPrincipal()
                .hasBranch(
                        organizationId,
                        branchId
                );
    }

    public boolean isOrganizationAdmin(UUID organizationId){
        return getPrincipal()
                .hasOrganizationAdminAccess(
                        organizationId
                );
    }

    public boolean canAccess(UUID organizationId, UUID branchId){

        if(isSystem()){
            return true;
        }

        if(isOrganizationAdmin(organizationId)){
            return true;
        }

        return hasBranchAccess(
                organizationId,
                branchId
        );
    }

    public UUID getCurrentOrganizationId(){
        return organizationContext
                .getOrganizationId();

    }

    public UUID getCurrentBranchId(){
        return organizationContext
                .getBranchId();

    }

    public boolean isCurrentOrganizationAdmin(){

        UUID organizationId =
                getCurrentOrganizationId();

        if(organizationId == null){
            return false;
        }

        return getPrincipal()
                .hasOrganizationAdminAccess(
                        organizationId
                );
    }

    public boolean isCurrentBranchAdmin(){

        UUID organizationId =
                getCurrentOrganizationId();

        UUID branchId =
                getCurrentBranchId();

        if(organizationId == null || branchId == null){
            return false;
        }

        return getPrincipal()
                .hasBranchAdminAccess(
                        organizationId,
                        branchId
                );
    }
}