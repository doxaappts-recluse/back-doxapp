package pe.dcs.app.features.pastoral.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.pastoral.dto.PastoralDtos;
import pe.dcs.app.features.pastoral.service.PastoralCaseService;
import pe.dcs.app.features.pastoral.service.PastoralRulesService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M12 · Cuidado pastoral (N2/N3, contratable): V consultar, C registrar, E editar/asignar/contactar/notas, S resolver/cerrar, H notas y casos reservados. */
@RestController
@RequestMapping("/api/v1/admin/pastoral-cases")
@RequiredArgsConstructor
public class AdminPastoralCaseController {

    private static final String MODULE = "PASTORAL_CARE";

    private final PastoralCaseService service;
    private final PastoralRulesService rules;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<PastoralDtos.CaseSummary>> search(@RequestBody(required = false) PastoralDtos.CaseSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/rules")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PastoralDtos.RulesResponse> rules() {
        return new ApiResponse<>(200, "ok.common.sent", rules.get(resolver.current().organizationId()));
    }

    @PutMapping("/rules")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PastoralDtos.RulesResponse> updateRules(@RequestBody PastoralDtos.RulesRequest req) {
        return new ApiResponse<>(200, "ok.pastoral.rulesUpdated", rules.update(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PastoralDtos.CaseResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor(), resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<PastoralDtos.CaseResponse> create(@RequestBody PastoralDtos.CaseCreateRequest req) {
        return new ApiResponse<>(201, "ok.pastoral.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}/assign")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PastoralDtos.CaseResponse> assign(@PathVariable UUID id, @RequestBody PastoralDtos.AssignRequest req) {
        return new ApiResponse<>(200, "ok.pastoral.assigned", service.assign(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PastoralDtos.CaseResponse> contact(@PathVariable UUID id, @RequestBody PastoralDtos.ContactRequest req) {
        return new ApiResponse<>(201, "ok.pastoral.contacted", service.addContact(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PastoralDtos.CaseResponse> addNote(@PathVariable UUID id, @RequestBody PastoralDtos.NoteRequest req) {
        return new ApiResponse<>(201, "ok.pastoral.noteAdded", service.addNote(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/resolve")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<PastoralDtos.CaseResponse> resolve(@PathVariable UUID id, @RequestBody PastoralDtos.ResolveRequest req) {
        return new ApiResponse<>(200, "ok.pastoral.resolved", service.resolve(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/close")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<PastoralDtos.CaseResponse> close(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.pastoral.closed", service.close(resolver.actor(), resolver.current(), id));
    }
}
