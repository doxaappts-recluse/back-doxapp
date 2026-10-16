package pe.dcs.app.features.training.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.features.training.service.CurriculumService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M13 · Catálogo de cursos disponibles para abrir un dictado (vinculados de la malla ACTIVE + extra de la sede) y CRUD de los cursos extra. */
@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private static final String MODULE = "TRAINING";

    private final CurriculumService service;
    private final AccessScopeResolver resolver;

    @GetMapping("/catalog")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<TrainingDtos.CourseView>> catalog(@RequestParam UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", service.catalog(resolver.current(), branchId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TrainingDtos.CourseView> createExtra(@RequestBody TrainingDtos.CourseRequest req) {
        return new ApiResponse<>(201, "ok.common.sent", service.createExtra(resolver.actor(), resolver.current(), req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> deleteExtra(@PathVariable UUID id) {
        service.deleteExtra(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }
}
