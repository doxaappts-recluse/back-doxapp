package pe.dcs.app.features.training.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.features.training.service.EnrollmentService;
import pe.dcs.app.features.training.service.TrainingCertificateService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.Map;
import java.util.UUID;

/** M13 · Matrículas: alta con prerrequisito, nota y estado (aprobar/reprobar/retirar), con certificado automático al aprobar. */
@RestController
@RequestMapping("/api/v1/admin/enrollments")
@RequiredArgsConstructor
public class AdminEnrollmentController {

    private static final String MODULE = "TRAINING";

    private final EnrollmentService service;
    private final TrainingCertificateService certificates;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<TrainingDtos.EnrollmentSummary>> search(@RequestBody(required = false) TrainingDtos.EnrollmentSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TrainingDtos.EnrollmentResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TrainingDtos.EnrollmentResponse> enroll(@RequestBody TrainingDtos.EnrollRequest req) {
        return new ApiResponse<>(201, "ok.training.enrolled", service.enroll(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}/grade")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TrainingDtos.EnrollmentResponse> grade(@PathVariable UUID id, @RequestBody TrainingDtos.GradeRequest req) {
        return new ApiResponse<>(200, "ok.training.graded", service.grade(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TrainingDtos.EnrollmentResponse> status(@PathVariable UUID id, @RequestBody TrainingDtos.StatusRequest req) {
        String key = req != null && "APPROVED".equalsIgnoreCase(req.status()) ? "ok.training.approved"
                : req != null && "FAILED".equalsIgnoreCase(req.status()) ? "ok.training.failed" : "ok.training.withdrawn";
        TrainingDtos.EnrollmentResponse res = service.status(resolver.actor(), resolver.current(), id, req);
        if (req != null && "APPROVED".equalsIgnoreCase(req.status())) {
            // M18 (seguimiento 2026-09-25): en su propia transacción, DESPUÉS de que status() ya confirmó la suya
            // (que ya emitió el certificado propio) — un fallo aquí nunca debe deshacer ni ocultar ese certificado.
            try {
                certificates.tryLinkM18(resolver.actor(), resolver.current(), id);
                res = service.get(resolver.current(), id);
            } catch (RuntimeException ignored) {
                // el certificado propio de M13 ya está emitido y es válido igual; el documento de M18 queda para otro intento futuro.
            }
        }
        return new ApiResponse<>(200, key, res);
    }

    @PostMapping("/{id}/certificate/void")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TrainingDtos.CertificateResponse> voidCertificate(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        TrainingDtos.CertificateResponse cert = certificates.voidCertificate(resolver.actor(), resolver.current(), id, body == null ? null : body.get("reason"));
        try {
            certificates.tryUnlinkM18(resolver.actor(), resolver.current(), cert.id());
        } catch (RuntimeException ignored) {
            // la anulación propia de M13 ya quedó registrada igual; el documento de M18, si había uno, se revisa a mano.
        }
        return new ApiResponse<>(200, "ok.common.sent", cert);
    }
}
