package pe.dcs.app.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.Exceptions;

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
            throw new Exceptions(
                    "error.usuarioNoAutenticado",
                    HttpStatus.UNAUTHORIZED
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

    public boolean isBranchAdmin(UUID organizationId, UUID branchId){
        return getPrincipal()
                .hasBranchAdminAccess(
                        organizationId,
                        branchId
                );
    }

    /**
     * Igual que {@link #isOrganizationAdmin(UUID)}, pero excluyendo
     * explícitamente a SYSTEM (que a nivel de credencial siempre
     * cuenta como admin). Útil quiere restringirse
     * exclusivamente a ORG_ADMIN/ORG_BRANCH_ADMIN, dejando fuera
     * incluso a SYSTEM.
     */
    public boolean isOrganizationAdminOnly(UUID organizationId){
        return !isSystem() && isOrganizationAdmin(organizationId);
    }

    /**
     * Igual que {@link #isBranchAdmin(UUID, UUID)}, pero excluyendo
     * explícitamente a SYSTEM.
     */
    public boolean isBranchAdminOnly(UUID organizationId, UUID branchId){
        return !isSystem() && isBranchAdmin(organizationId, branchId);
    }

    /**
     * Acceso exclusivo de ORG_ADMIN/ORG_BRANCH_ADMIN sobre un
     * org/sede puntual: SYSTEM queda explícitamente fuera.
     */
    public boolean canManageOrgOrBranchOnly(UUID organizationId, UUID branchId){
        return isOrganizationAdminOnly(organizationId)
                || isBranchAdminOnly(organizationId, branchId);
    }

    /**
     * Info de auditoría (quién/cuándo creó o actualizó un
     * registro) solo se muestra a SYSTEM, ORG_ADMIN y
     * ORG_BRANCH_ADMIN. Un ORG_USER normal no la ve.
     */
    public boolean canViewAudit(){
        return isSystem()
                || isCurrentOrganizationAdmin()
                || isCurrentBranchAdmin();
    }

    /**
     * Chequeo genérico de "puede gestionar" reutilizado por varias
     * features (Bautizo, Membresía, Traslados, Servicio
     * Ministerial): SYSTEM, admin de la organización actual o
     * admin de la sede actual. Centralizado acá para que cada
     * ServiceImpl no reimplemente el mismo if/throw con su propio
     * mensaje — ver {@link #assertCanManageCurrent(String)}.
     */
    public boolean canManageCurrent() {
        return isSystem()
                || isCurrentOrganizationAdmin()
                || isCurrentBranchAdmin();
    }

    /**
     * Igual que {@link #canManageCurrent()} pero lanzando
     * FORBIDDEN con el mensaje propio del feature que llama —
     * cada dominio conserva su texto de error, solo se comparte la
     * condición.
     */
    public void assertCanManageCurrent(String message) {

        if (!canManageCurrent()) {
            throw new Exceptions(message, HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Igual que {@link #canManageOrgOrBranchOnly(UUID, UUID)} pero
     * usando la organización/sede actuales del contexto (excluye
     * SYSTEM a propósito) — evita que cada caller repita
     * getCurrentOrganizationId()/getCurrentBranchId() y el
     * if/throw asociado.
     */
    public void assertCanManageOrgOrBranchOnlyForCurrent(String message) {

        if (!canManageOrgOrBranchOnly(
                getCurrentOrganizationId(),
                getCurrentBranchId()
        )) {
            throw new Exceptions(message, HttpStatus.FORBIDDEN);
        }
    }
}