package features.rite.web;

import features.rite.dto.RiteDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import features.rite.service.CertificateService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

/** M08 · Verificación pública de un certificado por su código: solo estado, tipo, número, fecha y nombre abreviado. */
@RestController
@RequestMapping("/api/v1/public/certificates")
@PublicEndpoint
@RequiredArgsConstructor
public class PublicCertificateController {

    private final CertificateService certificates;

    @GetMapping("/{code}")
    public ApiResponse<RiteDtos.CertificateVerification> verify(@PathVariable String code) {
        return new ApiResponse<>(200, "ok.common.sent", certificates.verify(code));
    }
}
