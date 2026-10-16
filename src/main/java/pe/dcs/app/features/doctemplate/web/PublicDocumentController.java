package pe.dcs.app.features.doctemplate.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.doctemplate.service.DocumentPublicRateLimiter;
import pe.dcs.app.features.doctemplate.service.IssuedDocumentService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.Exceptions;

/** M18 · Verificación pública de un documento por su código (sin sesión): solo estado, tipo, número, fecha y nombre abreviado. */
@RestController
@RequestMapping("/api/v1/public/v")
@PublicEndpoint
@RequiredArgsConstructor
public class PublicDocumentController {

    private final IssuedDocumentService service;
    private final DocumentPublicRateLimiter limiter;

    @GetMapping("/{code}")
    public ApiResponse<TemplateDtos.VerificationResult> verify(@PathVariable String code, HttpServletRequest http) {
        if (!limiter.tryAcquire(http.getRemoteAddr())) {
            throw new Exceptions("error.common.rateLimited", HttpStatus.TOO_MANY_REQUESTS, 60);
        }
        return new ApiResponse<>(200, "ok.common.sent", service.verify(code));
    }
}
