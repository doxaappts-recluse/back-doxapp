package pe.dcs.app.security.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;

import java.util.UUID;

@Component
public class AuthContext {

    public CredentialDetailsImpl getPrincipal() {
        return (CredentialDetailsImpl) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public UUID getUserId() {
        return getPrincipal().getUserId();
    }

    public UUID getOrganizationId() {
        return getPrincipal().getOrganizationId();
    }

    public boolean isSystemUser() {
        return hasRole("SYSTEM");
    }

    public boolean isOrgAdmin() {
        return hasRole("ORG_ADMIN");
    }

    public boolean isOrgUser() {
        return hasRole("ORG_USER");
    }

    public boolean hasRole(String role) {
        return getPrincipal()
                .getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}