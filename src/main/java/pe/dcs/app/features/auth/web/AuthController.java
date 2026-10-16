package pe.dcs.app.features.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.auth.dto.AcceptInviteRequest;
import pe.dcs.app.features.auth.dto.AuthResponse;
import pe.dcs.app.features.auth.dto.ChangePasswordRequest;
import pe.dcs.app.features.auth.dto.ContextRequest;
import pe.dcs.app.features.auth.dto.DisableMfaRequest;
import pe.dcs.app.features.auth.dto.ForgotPasswordRequest;
import pe.dcs.app.features.auth.dto.LoginRequest;
import pe.dcs.app.features.auth.dto.LogoutRequest;
import pe.dcs.app.features.auth.dto.MfaCodeRequest;
import pe.dcs.app.features.auth.dto.MfaSetupResponse;
import pe.dcs.app.features.auth.dto.RefreshRequest;
import pe.dcs.app.features.auth.dto.ResetPasswordRequest;
import pe.dcs.app.features.auth.service.AuthenticationService;
import pe.dcs.app.features.auth.service.ClientInfo;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.PublicEndpoint;

import java.util.Optional;

/**
 * Autenticación (M05). Sin @ModuleAccess por diseño: quién puede llamar a cada ruta lo decide SecurityConfig por
 * tipo de token (login/refresh públicos; MFA/contexto/setup con token intermedio).
 */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService service;
    private final AccessScopeResolver resolver;

    /** Login directo (usuario + contraseña): el servidor resuelve a qué organización (o plataforma) pertenece. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.login(req, ClientInfo.from(http));
    }

    @PostMapping("/platform/login")
    public AuthResponse platformLogin(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.loginPlatform(req, ClientInfo.from(http));
    }

    @PostMapping("/o/{slug}/login")
    public AuthResponse organizationLogin(@PathVariable String slug, @Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return service.loginOrganization(slug, req, ClientInfo.from(http));
    }

    @PostMapping("/mfa/verify")
    public AuthResponse mfaVerify(@Valid @RequestBody MfaCodeRequest req, HttpServletRequest http) {
        return service.verifyMfa(req, resolver.actor(), ClientInfo.from(http));
    }

    @PostMapping("/context")
    public AuthResponse context(@Valid @RequestBody ContextRequest req, HttpServletRequest http) {
        return service.selectContext(req, resolver.actor(), ClientInfo.from(http));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return service.refresh(req.refreshToken(), ClientInfo.from(http));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest req) {
        service.logout(req == null ? null : req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        service.forgotPassword(req, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        service.resetPassword(req, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<AuthResponse> change(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest http) {
        return respond(service.changePassword(req, resolver.actor(), ClientInfo.from(http)));
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest req, HttpServletRequest http) {
        service.acceptInvite(req, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/setup")
    public MfaSetupResponse mfaSetup() {
        return service.mfaSetup(resolver.actor());
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<AuthResponse> mfaEnable(@Valid @RequestBody MfaCodeRequest req, HttpServletRequest http) {
        return respond(service.mfaEnable(req, resolver.actor(), ClientInfo.from(http)));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Void> mfaDisable(@Valid @RequestBody DisableMfaRequest req) {
        service.mfaDisable(req, resolver.actor());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<AuthResponse> respond(Optional<AuthResponse> body) {
        return body.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
