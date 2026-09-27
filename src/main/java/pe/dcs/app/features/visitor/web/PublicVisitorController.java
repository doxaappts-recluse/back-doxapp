package pe.dcs.app.features.visitor.web;

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
import pe.dcs.app.features.visitor.dto.VisitorDtos;
import pe.dcs.app.features.visitor.service.VisitorPublicService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

/** M07 · Formulario público de bienvenida (sin sesión). La organización sin el módulo contratado responde 404. */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/public/orgs/{slug}/visit")
@RequiredArgsConstructor
public class PublicVisitorController {

    private final VisitorPublicService service;

    @GetMapping
    public ResponseEntity<VisitorDtos.PublicConfig> config(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.config(slug));
    }

    @PostMapping
    public ApiResponse<Void> submit(@PathVariable String slug, @RequestBody VisitorDtos.PublicRequest req, HttpServletRequest http) {
        service.submit(slug, http.getRemoteAddr(), req);
        return new ApiResponse<>(200, "msg.visitor.thanks", null);
    }
}
