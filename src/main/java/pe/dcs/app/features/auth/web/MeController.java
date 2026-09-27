package pe.dcs.app.features.auth.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.auth.dto.ContextInfo;
import pe.dcs.app.features.auth.dto.MeResponse;
import pe.dcs.app.features.auth.dto.MenuResponse;
import pe.dcs.app.features.auth.dto.SessionInfo;
import pe.dcs.app.features.auth.service.AuthenticationService;
import pe.dcs.app.features.auth.service.MenuService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.PublicEndpoint;

import java.util.List;
import java.util.UUID;

/** Cuenta propia: cualquier token de acceso puede consultar lo suyo (no requiere permiso de módulo). */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final AuthenticationService auth;
    private final MenuService menus;
    private final AccessScopeResolver resolver;

    @GetMapping
    public MeResponse me() {
        return auth.me(resolver.actor());
    }

    @GetMapping("/menu")
    public MenuResponse menu() {
        return menus.menu(resolver.actor());
    }

    @GetMapping("/contexts")
    public List<ContextInfo> contexts() {
        return auth.myContexts(resolver.actor());
    }

    @GetMapping("/sessions")
    public List<SessionInfo> sessions() {
        return auth.mySessions(resolver.actor());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        auth.revokeSession(resolver.actor(), id);
        return ResponseEntity.noContent().build();
    }
}
