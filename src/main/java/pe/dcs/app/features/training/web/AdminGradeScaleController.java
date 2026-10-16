package pe.dcs.app.features.training.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.features.training.service.GradeScaleService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M13 · Escala de notas por organización (mínimo, máximo, nota de aprobación) [V5]. Solo el administrador de la organización la edita. */
@RestController
@RequestMapping("/api/v1/admin/training/grade-scale")
@RequiredArgsConstructor
public class AdminGradeScaleController {

    private static final String MODULE = "TRAINING";

    private final GradeScaleService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TrainingDtos.GradeScaleResponse> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current().organizationId()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TrainingDtos.GradeScaleResponse> update(@RequestBody TrainingDtos.GradeScaleRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.update(resolver.actor(), resolver.current(), req));
    }
}
