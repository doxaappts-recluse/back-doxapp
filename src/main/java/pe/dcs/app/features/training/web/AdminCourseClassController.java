package pe.dcs.app.features.training.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.training.dto.TrainingDtos;
import pe.dcs.app.features.training.service.CourseClassService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M13 · Dictados (course_class): sede, docente, horario y asistencia (núcleo M09, contexto CLASS). */
@RestController
@RequestMapping("/api/v1/admin/classes")
@RequiredArgsConstructor
public class AdminCourseClassController {

    private static final String MODULE = "TRAINING";

    private final CourseClassService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<TrainingDtos.ClassSummary>> search(@RequestBody(required = false) TrainingDtos.ClassSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TrainingDtos.ClassResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TrainingDtos.ClassResponse> create(@RequestBody TrainingDtos.ClassRequest req) {
        return new ApiResponse<>(201, "ok.common.sent", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TrainingDtos.ClassResponse> update(@PathVariable UUID id, @RequestBody TrainingDtos.ClassRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @PostMapping("/{id}/start")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TrainingDtos.ClassResponse> start(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.training.classStarted", service.start(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/complete")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TrainingDtos.ClassResponse> complete(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.training.classCompleted", service.complete(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TrainingDtos.ClassResponse> cancel(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return new ApiResponse<>(200, "ok.training.classCancelled", service.cancel(resolver.actor(), resolver.current(), id, body == null ? null : body.get("reason")));
    }

    @GetMapping("/{id}/sessions")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<TrainingDtos.SessionView>> sessions(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.sessions(resolver.current(), id));
    }

    @PostMapping("/{id}/sessions/{date}/attendance")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<List<TrainingDtos.SessionView>> mark(@PathVariable UUID id, @PathVariable LocalDate date, @RequestBody TrainingDtos.MarkAttendanceRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.mark(resolver.actor(), resolver.current(), id, date, req));
    }
}
