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

/** M13 · Malla curricular y sus cursos vinculados (por nivel). Solo el administrador de la organización crea/edita [N2]. */
@RestController
@RequestMapping("/api/v1/admin/curricula")
@RequiredArgsConstructor
public class AdminCurriculumController {

    private static final String MODULE = "TRAINING";

    private final CurriculumService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<TrainingDtos.CurriculumSummary>> list(@RequestParam(required = false) String status) {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current(), status));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TrainingDtos.CurriculumResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TrainingDtos.CurriculumResponse> create(@RequestBody TrainingDtos.CurriculumRequest req) {
        return new ApiResponse<>(201, "ok.common.sent", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TrainingDtos.CurriculumResponse> update(@PathVariable UUID id, @RequestBody TrainingDtos.CurriculumRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @PostMapping("/{id}/activate")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TrainingDtos.CurriculumResponse> activate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.training.curriculumActivated", service.activate(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TrainingDtos.CurriculumResponse> addCourse(@PathVariable UUID id, @RequestBody TrainingDtos.CourseRequest req) {
        return new ApiResponse<>(201, "ok.common.sent", service.addCourse(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/courses/{courseId}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TrainingDtos.CurriculumResponse> updateCourse(@PathVariable UUID id, @PathVariable UUID courseId, @RequestBody TrainingDtos.CourseRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.updateCourse(resolver.actor(), resolver.current(), id, courseId, req));
    }

    @DeleteMapping("/{id}/courses/{courseId}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<TrainingDtos.CurriculumResponse> removeCourse(@PathVariable UUID id, @PathVariable UUID courseId) {
        return new ApiResponse<>(200, "ok.common.sent", service.removeCourse(resolver.actor(), resolver.current(), id, courseId));
    }

    @PutMapping("/{id}/courses/reorder")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TrainingDtos.CurriculumResponse> reorder(@PathVariable UUID id, @RequestBody TrainingDtos.ReorderRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.reorder(resolver.actor(), resolver.current(), id, req));
    }
}
