package pe.dcs.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.User;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.Exceptions;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    // =========================================================
    // ROLE CHECKS
    // =========================================================

    public boolean isSystem(CredentialDetailsImpl user) {
        return hasPrefix(user, "SYSTEM");
    }

    public boolean isOrgAdmin(CredentialDetailsImpl user) {
        return hasRole(user, "ORG_ADMIN");
    }

    public boolean isOrgUser(CredentialDetailsImpl user) {
        return hasRole(user, "ORG_USER");
    }

    public boolean isOrganizationUser(CredentialDetailsImpl user) {
        return isOrgAdmin(user) || isOrgUser(user);
    }


    // =========================================================
    // HELPERS
    // =========================================================

    private boolean hasRole(CredentialDetailsImpl user, String role) {
        return user.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private boolean hasPrefix(CredentialDetailsImpl user, String prefix) {
        return user.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().startsWith(prefix));
    }

    // =========================================================
    // ORGANIZATION CONTEXT
    // =========================================================

    public UUID getOrganizationId(CredentialDetailsImpl user) {
        if (user.getOrganizationId() == null) {
            throw new Exceptions("User without organization", HttpStatus.BAD_REQUEST);
        }
        return user.getOrganizationId();
    }

    public boolean sameOrganization(CredentialDetailsImpl a, CredentialDetailsImpl b) {
        return getOrganizationId(a).equals(getOrganizationId(b));
    }

    public boolean sameOrganization(CredentialDetailsImpl user, UUID organizationId) {
        return getOrganizationId(user).equals(organizationId);
    }

    // =========================================================
    // SECURITY RULES
    // =========================================================

    public void assertCanAccessUser(CredentialDetailsImpl actor, CredentialDetailsImpl target) {

        if (isSystem(actor)) return;

        if (!isOrganizationUser(actor)) {
            throw new Exceptions("Invalid role", HttpStatus.FORBIDDEN);
        }

        if (isSystem(target)) {
            throw new Exceptions("Cannot manage system users", HttpStatus.FORBIDDEN);
        }

        if (!sameOrganization(actor, target.getOrganizationId())) {
            throw new Exceptions("Different organization", HttpStatus.FORBIDDEN);
        }
    }

    public void assertCanAccessOrganization(CredentialDetailsImpl actor, UUID organizationId) {

        if (isSystem(actor)) return;

        if (!isOrganizationUser(actor)) {
            throw new Exceptions("Invalid role", HttpStatus.FORBIDDEN);
        }

        if (!sameOrganization(actor, organizationId)) {
            throw new Exceptions("Organization access denied", HttpStatus.FORBIDDEN);
        }
    }
}