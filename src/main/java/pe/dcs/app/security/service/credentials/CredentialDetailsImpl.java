package pe.dcs.app.security.service.credentials;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

@Getter
public class CredentialDetailsImpl implements UserDetails {

    private UUID id;
    private UUID userId;
    private UUID organizationId;

    private String username;
    private String password;

    private String nombre;
    private String apellido;

    private boolean enabled;

    private Collection<? extends GrantedAuthority> authorities;

    public CredentialDetailsImpl(
            UUID id,
            UUID userId,
            UUID organizationId,
            String username,
            String password,
            String nombre,
            String apellido,
            boolean enabled,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.id = id;
        this.userId = userId;
        this.organizationId = organizationId;
        this.username = username;
        this.password = password;
        this.nombre = nombre;
        this.apellido = apellido;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    // =========================
    // ROLE HELPERS (DERIVADOS)
    // =========================

    public boolean hasRole(String role) {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    public boolean hasAnyRole(String prefix) {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().startsWith(prefix));
    }

    public boolean isSystem() {
        return hasAnyRole("SYSTEM_");
    }

    public boolean isOrgAdmin() {
        return hasRole("ORG_ADMIN");
    }

    public boolean isOrgUser() {
        return hasRole("ORG_USER");
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}