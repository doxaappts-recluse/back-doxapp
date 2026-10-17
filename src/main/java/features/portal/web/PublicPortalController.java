package features.portal.web;

import features.portal.dto.PortalDtos;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import features.portal.service.PortalSignupPublicService;
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
    public ResponseEntity<PortalDtos.SignupPublicConfig> config(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.config(slug));
    }

    @PostMapping
    public ApiResponse<Void> submit(@PathVariable String slug, @RequestBody PortalDtos.SignupPublicRequest req, HttpServletRequest http) {
        service.submit(slug, http.getRemoteAddr(), req);
        return new ApiResponse<>(200, "msg.portal.signupReceived", null);
    }

    /** HALLAZGO M24-E06: ver {@link PortalSignupPublicService#status}. */
    @GetMapping("/status")
    public ResponseEntity<PortalDtos.SignupStatusResponse> status(@PathVariable String slug, @RequestParam String docType,
                                                                  @RequestParam String docNumber, HttpServletRequest http) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.status(slug, http.getRemoteAddr(), docType, docNumber));
    }
}
