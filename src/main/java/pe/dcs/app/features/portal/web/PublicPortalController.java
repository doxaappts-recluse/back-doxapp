package pe.dcs.app.features.portal.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.portal.dto.PortalDtos.SignupPublicConfig;
import pe.dcs.app.features.portal.dto.PortalDtos.SignupPublicRequest;
import pe.dcs.app.features.portal.service.PortalSignupPublicService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

/**
 * M24 [V8] · autorregistro público (sin sesión). El branding de la pantalla de ingreso ya lo sirve
 * {@code PublicBrandingController} (M02: /public/orgs/{slug}/branding) — no se repite aquí.
 */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/public/orgs/{slug}/portal/signup")
@RequiredArgsConstructor
public class PublicPortalController {

    private final PortalSignupPublicService service;

    @GetMapping
    public ResponseEntity<SignupPublicConfig> config(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.config(slug));
    }

    @PostMapping
    public ApiResponse<Void> submit(@PathVariable String slug, @RequestBody SignupPublicRequest req, HttpServletRequest http) {
        service.submit(slug, http.getRemoteAddr(), req);
        return new ApiResponse<>(200, "msg.portal.signupReceived", null);
    }
}
