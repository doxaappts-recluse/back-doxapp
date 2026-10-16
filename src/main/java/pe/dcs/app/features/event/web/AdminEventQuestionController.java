package pe.dcs.app.features.event.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.event.service.EventService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

/** M14 · Preguntas del formulario de inscripción de un evento. */
@RestController
@RequestMapping("/api/v1/admin/event-questions")
@RequiredArgsConstructor
public class AdminEventQuestionController {

    private static final String MODULE = "EVENTS";

    private final EventService service;
    private final AccessScopeResolver resolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<EventDtos.EventDetail> create(@RequestBody EventDtos.QuestionCreateRequest req) {
        return new ApiResponse<>(201, "ok.common.sent", service.addQuestion(resolver.actor(), resolver.current(), req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.removeQuestionById(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }
}
