package pe.dcs.app.security.service.credentials;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pe.dcs.app.security.service.UserAccessContext;
import pe.dcs.app.util.enums.RoleType;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static pe.dcs.app.util.enums.RoleType.SYSTEM_ADMIN;

@Getter
public class CredentialDetailsImpl implements UserDetails {

    private final UUID credentialId;
    private final UUID userId;

    private final String username;
    private final String password;

    private final String name;
    private final String lastname;

    private final boolean enabled;

    /**
     * Todos los accesos del usuario.
     *
     * SYSTEM:
     * organizationId = null
     * branchId = null
     *
     * ORG:
     * organizationId != null
     */
    private final List<UserAccessContext> accesses;

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Contexto actual del JWT.
     */
    private final UUID currentOrganizationId;
    private final UUID currentBranchId;

    public CredentialDetailsImpl(
            UUID credentialId,
            UUID userId,
            String username,
            String password,
            String name,
            String lastname,
            boolean enabled,
            List<UserAccessContext> accesses,
            Collection<? extends GrantedAuthority> authorities,
            UUID currentOrganizationId,
            UUID currentBranchId
    ) {

        this.credentialId = credentialId;
        this.userId = userId;

        this.username = username;
        this.password = password;

        this.name = name;
        this.lastname = lastname;

        this.enabled = enabled;

        this.accesses = accesses;
        this.authorities = authorities;

        this.currentOrganizationId = currentOrganizationId;
        this.currentBranchId = currentBranchId;
    }

    /**
     * Cambia el contexto actual del usuario
     * usando el JWT contextual.
     */
    public CredentialDetailsImpl withContext(
            UUID organizationId,
            UUID branchId
    ){

        return new CredentialDetailsImpl(
                credentialId,
                userId,
                username,
                password,
                name,
                lastname,
                enabled,
                accesses,
                authorities,
                organizationId,
                branchId
        );
    }

    // =====================================================
    // ROLE
    // =====================================================

    public boolean hasRole(String role){

        return authorities
                .stream()
                .anyMatch(
                        a ->
                                a.getAuthority()
                                        .equals(role)
                );
    }

    public boolean isSystemAdmin(){

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                SYSTEM_ADMIN.equals(
                                        a.roleCode()
                                )
                );
    }

    public boolean isSystemSupport(){

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                RoleType.SYSTEM_SUPPORT.equals(
                                        a.roleCode()
                                )
                );
    }

    // =====================================================
    // ORGANIZATION ACCESS
    // =====================================================

    public boolean hasOrganization(
            UUID organizationId
    ){

        if(isSystemAdmin() || isSystemSupport()){
            return true;
        }

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                organizationId != null
                                        &&
                                        organizationId.equals(
                                                a.organizationId()
                                        )
                );
    }

    public boolean hasBranch(
            UUID organizationId,
            UUID branchId
    ){

        if(isSystemAdmin() || isSystemSupport()){
            return true;
        }

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                organizationId != null
                                        &&
                                        organizationId.equals(
                                                a.organizationId()
                                        )
                                        &&
                                        branchId != null
                                        &&
                                        branchId.equals(
                                                a.branchId()
                                        )
                );
    }

    // =====================================================
    // ADMIN ACCESS
    // =====================================================

    public boolean hasOrganizationAdminAccess(
            UUID organizationId
    ){

        if(isSystemAdmin() || isSystemSupport()){
            return true;
        }

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                organizationId != null
                                        &&
                                        organizationId.equals(
                                                a.organizationId()
                                        )
                                        &&
                                        a.branchId() == null
                                        &&
                                        RoleType.ORG_ADMIN.equals(
                                                a.roleCode()
                                        )
                );
    }

    public boolean hasBranchAdminAccess(
            UUID organizationId,
            UUID branchId
    ){

        if(isSystemAdmin() || isSystemSupport()){
            return true;
        }

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                organizationId != null
                                        &&
                                        organizationId.equals(
                                                a.organizationId()
                                        )
                                        &&
                                        branchId != null
                                        &&
                                        branchId.equals(
                                                a.branchId()
                                        )
                                        &&
                                        RoleType.ORG_BRANCH_ADMIN.equals(
                                                a.roleCode()
                                        )
                );
    }

    public boolean hasOrganizationUser(
            UUID organizationId,
            UUID branchId
    ){

        if(isSystemAdmin() || isSystemSupport()){
            return true;
        }

        return accesses
                .stream()
                .anyMatch(
                        a ->
                                Objects.equals(
                                        organizationId,
                                        a.organizationId()
                                )
                                        &&
                                        Objects.equals(
                                                branchId,
                                                a.branchId()
                                        )
                                        &&
                                        RoleType.ORG_USER.equals(
                                                a.roleCode()
                                        )
                );
    }

    public List<UserAccessContext> getOrganizationAccesses(
            UUID organizationId
    ){

        if(isSystemAdmin() || isSystemSupport()){
            return accesses;
        }

        return accesses
                .stream()
                .filter(
                        a ->
                                organizationId != null
                                        &&
                                        organizationId.equals(
                                                a.organizationId()
                                        )
                )
                .toList();
    }

    // =====================================================
    // CURRENT CONTEXT
    // =====================================================

    public boolean hasCurrentOrganization(){
        return currentOrganizationId != null;
    }

    public boolean hasCurrentBranch(){
        return currentBranchId != null;
    }

    // =====================================================
    // SPRING SECURITY
    // =====================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return authorities;
    }

    @Override
    public String getPassword(){
        return password;
    }

    @Override
    public String getUsername(){
        return username;
    }

    @Override
    public boolean isAccountNonExpired(){
        return true;
    }

    @Override
    public boolean isAccountNonLocked(){
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired(){
        return true;
    }

    @Override
    public boolean isEnabled(){
        return enabled;
    }

}