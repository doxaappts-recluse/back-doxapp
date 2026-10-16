package pe.dcs.app.features.rite.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.features.rite.service.MembershipService;
import pe.dcs.app.features.rite.service.RiteRequirementService;
import pe.dcs.app.features.rite.service.RiteRulesService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M08 · Membresía (módulo contratable MEMBERSHIP): V consultar, C solicitar, E editar/terminar/cancelar, A aprobar y registrar directamente,
 * H ver notas de salida disciplinaria, O omitir requisitos con motivo. Los permisos finos de cada operación se comprueban en el servicio.
 */
@RestController
@RequestMapping("/api/v1/admin/memberships")
@ModuleAccess(module = "MEMBERSHIP", action = Action.V)
@RequiredArgsConstructor
public class AdminMembershipController {

    private final MembershipService service;
    private final RiteRequirementService requirements;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    public ApiResponse<PageResponse<RiteDtos.MembershipResponse>> search(@RequestBody(required = false) RiteDtos.MembershipSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<RiteDtos.MembershipResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor(), resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RiteDtos.MembershipResponse> create(@RequestBody RiteDtos.MembershipRequest req) {
        return new ApiResponse<>(201, "ok.membership.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<RiteDtos.MembershipResponse> update(@PathVariable UUID id, @RequestBody RiteDtos.MembershipUpdate req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/end")
    public ApiResponse<RiteDtos.MembershipResponse> end(@PathVariable UUID id, @RequestBody RiteDtos.EndRequest req) {
        return new ApiResponse<>(200, "ok.membership.ended", service.end(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<RiteDtos.MembershipResponse> cancel(@PathVariable UUID id, @RequestBody RiteDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.rite.cancelled", service.cancel(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<RiteDtos.MembershipResponse> approve(@PathVariable UUID id, @RequestBody(required = false) RiteDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.common.approved", service.approve(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<RiteDtos.MembershipResponse> reject(@PathVariable UUID id, @RequestBody RiteDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.common.rejected", service.reject(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/override")
    public ApiResponse<RiteDtos.MembershipResponse> override(@PathVariable UUID id, @RequestBody RiteDtos.OverrideRequest req) {
        return new ApiResponse<>(200, "ok.rite.overridden", service.override(resolver.actor(), resolver.current(), id, req));
    }

    @GetMapping("/{id}/requirements")
    public ApiResponse<RiteDtos.Checklist> checklist(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.checklist(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}/requirements/{requirementId}")
    public ApiResponse<RiteDtos.Checklist> mark(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody RiteDtos.CheckRequest req) {
        return new ApiResponse<>(200, "ok.rite.checked", service.mark(resolver.actor(), resolver.current(), id, requirementId, req));
    }

    // ---------------------------------------------------------------- configuración de requisitos (administrador de la organización)

    @GetMapping("/requirements")
    public ApiResponse<List<RiteDtos.RequirementResponse>> requirementList(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return new ApiResponse<>(200, "ok.common.sent", requirements.list(resolver.actor(), resolver.current(), "MEMBERSHIP", onlyActive));
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RiteDtos.RequirementResponse> requirementCreate(@RequestBody RiteDtos.RequirementRequest req) {
        return new ApiResponse<>(201, "ok.rite.requirementSaved", requirements.create(resolver.actor(), resolver.current(), withType(req)));
    }

    @PutMapping("/requirements/{requirementId}")
    public ApiResponse<RiteDtos.RequirementResponse> requirementUpdate(@PathVariable UUID requirementId, @RequestBody RiteDtos.RequirementRequest req) {
        return new ApiResponse<>(200, "ok.rite.requirementSaved", requirements.update(resolver.actor(), resolver.current(), requirementId, withType(req)));
    }

    @DeleteMapping("/requirements/{requirementId}")
    public ApiResponse<Void> requirementDelete(@PathVariable UUID requirementId) {
        requirements.delete(resolver.actor(), resolver.current(), requirementId);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }

    private static RiteDtos.RequirementRequest withType(RiteDtos.RequirementRequest r) {
        if (r == null) {
            return null;
        }
        return new RiteDtos.RequirementRequest("MEMBERSHIP", r.code(), r.label(), r.required(), r.source(), r.minAge(), r.active(), r.sortOrder(), r.version());
    }
}
