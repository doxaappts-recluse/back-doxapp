package pe.dcs.app.features.training.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.features.training.service.TrainingCertificateService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

/** M13 · Verificación pública de un certificado de formación por su código: solo estado, número, fecha y nombre abreviado. */
@RestController
@RequestMapping("/api/v1/public/training-certificates")
@PublicEndpoint
@RequiredArgsConstructor
public class PublicTrainingCertificateController {

    private final TrainingCertificateService certificates;

    @GetMapping("/{code}")
    public ApiResponse<TrainingDtos.CertificateVerification> verify(@PathVariable String code) {
        return new ApiResponse<>(200, "ok.common.sent", certificates.verify(code));
    }
}
