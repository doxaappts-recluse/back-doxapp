package pe.dcs.app.features.rite.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import pe.dcs.app.features.rite.dto.RiteDtos;
import pe.dcs.app.features.rite.service.CertificateService;
import pe.dcs.app.features.rite.service.RiteRequirementService;
import pe.dcs.app.features.rite.service.RiteRulesService;
import pe.dcs.app.features.rite.service.RiteService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M08 · Base de los controladores de bautizo, matrimonio y presentación de niños. Cada controlador concreto fija el tipo, la ruta y el módulo (V a nivel de clase);
 * los permisos de cada operación (C, E, D, A, O) los comprueba el servicio con el módulo del tipo.
 */
abstract class AbstractRiteController {

    private final String type;
    private final RiteService service;
    private final CertificateService certificates;
    private final RiteRequirementService requirements;
    private final RiteRulesService rules;
    private final AccessScopeResolver resolver;

    AbstractRiteController(String type, RiteService service, CertificateService certificates, RiteRequirementService requirements, RiteRulesService rules, AccessScopeResolver resolver) {
        this.type = type;
        this.service = service;
        this.certificates = certificates;
        this.requirements = requirements;
        this.rules = rules;
        this.resolver = resolver;
    }

    @PostMapping("/search")
    public ApiResponse<PageResponse<RiteDtos.RiteResponse>> search(@RequestBody(required = false) RiteDtos.RiteSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), type, req));
    }

    @GetMapping("/{id}")
    public ApiResponse<RiteDtos.RiteResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), type, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RiteDtos.RiteResponse> create(@RequestBody RiteDtos.RiteRequest req) {
        return new ApiResponse<>(201, "ok.rite.created", service.create(resolver.actor(), resolver.current(), type, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<RiteDtos.RiteResponse> update(@PathVariable UUID id, @RequestBody RiteDtos.RiteUpdate req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), type, id, req));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<RiteDtos.RiteResponse> approve(@PathVariable UUID id, @RequestBody(required = false) RiteDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.common.approved", service.approve(resolver.actor(), resolver.current(), type, id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<RiteDtos.RiteResponse> reject(@PathVariable UUID id, @RequestBody RiteDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.common.rejected", service.reject(resolver.actor(), resolver.current(), type, id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/override")
    public ApiResponse<RiteDtos.RiteResponse> override(@PathVariable UUID id, @RequestBody RiteDtos.OverrideRequest req) {
        return new ApiResponse<>(200, "ok.rite.overridden", service.override(resolver.actor(), resolver.current(), type, id, req));
    }

    @GetMapping("/{id}/requirements")
    public ApiResponse<RiteDtos.Checklist> checklist(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.checklist(resolver.current(), type, id));
    }

    @PutMapping("/{id}/requirements/{requirementId}")
    public ApiResponse<RiteDtos.Checklist> mark(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody RiteDtos.CheckRequest req) {
        return new ApiResponse<>(200, "ok.rite.checked", service.mark(resolver.actor(), resolver.current(), type, id, requirementId, req));
    }

    @PostMapping("/{id}/schedule")
    public ApiResponse<RiteDtos.RiteResponse> schedule(@PathVariable UUID id, @RequestBody RiteDtos.ScheduleRequest req) {
        return new ApiResponse<>(200, "ok.rite.scheduled", service.schedule(resolver.actor(), resolver.current(), type, id, req));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<RiteDtos.RiteResponse> complete(@PathVariable UUID id, @RequestBody(required = false) RiteDtos.CompleteRequest req) {
        return new ApiResponse<>(200, "ok.rite.completed", service.complete(resolver.actor(), resolver.current(), type, id, req));
    }

    @PostMapping("/{id}/confirm-spouse")
    public ApiResponse<RiteDtos.RiteResponse> confirmSpouse(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.rite.spouseConfirmed", service.confirmSpouse(resolver.actor(), resolver.current(), type, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<RiteDtos.RiteResponse> cancel(@PathVariable UUID id, @RequestBody RiteDtos.ReasonRequest req) {
        return new ApiResponse<>(200, "ok.rite.cancelled", service.cancel(resolver.actor(), resolver.current(), type, id, req == null ? null : req.reason()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), type, id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }

    // ---------------------------------------------------------------- certificado

    @GetMapping("/{id}/certificate")
    public ApiResponse<RiteDtos.CertificateResponse> certificate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", certificates.current(resolver.current(), type, id));
    }

    @PostMapping("/{id}/certificate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RiteDtos.CertificateResponse> issue(@PathVariable UUID id) {
        RiteDtos.CertificateResponse cert = certificates.issue(resolver.actor(), resolver.current(), type, id);
        // M18 (seguimiento 2026-09-25): intento aparte, en su propia transacción y DESPUÉS de que el certificado
        // propio ya se confirmó arriba — un fallo aquí (sin plantilla configurada, error de render) nunca debe
        // deshacer ni ocultar el certificado que el usuario ya recibió.
        try {
            certificates.tryLinkM18(resolver.actor(), resolver.current(), type, id);
            cert = certificates.current(resolver.current(), type, id);
        } catch (RuntimeException ignored) {
            // el certificado propio de M08 ya está emitido y es válido igual; el documento de M18 queda para otro intento futuro.
        }
        return new ApiResponse<>(201, "ok.rite.certificateIssued", cert);
    }

    @PostMapping("/{id}/certificate/void")
    public ApiResponse<RiteDtos.CertificateResponse> voidCertificate(@PathVariable UUID id, @RequestBody RiteDtos.ReasonRequest req) {
        RiteDtos.CertificateResponse cert = certificates.voidCertificate(resolver.actor(), resolver.current(), type, id, req == null ? null : req.reason());
        try {
            certificates.tryUnlinkM18(resolver.actor(), resolver.current(), cert.id());
        } catch (RuntimeException ignored) {
            // la anulación propia de M08 ya quedó registrada igual; el documento de M18, si había uno, se revisa a mano.
        }
        return new ApiResponse<>(200, "ok.rite.certificateVoided", cert);
    }

    // ---------------------------------------------------------------- requisitos y reglas (administrador de la organización)

    @GetMapping("/requirements")
    public ApiResponse<List<RiteDtos.RequirementResponse>> requirementList(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return new ApiResponse<>(200, "ok.common.sent", requirements.list(resolver.actor(), resolver.current(), type, onlyActive));
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

    @GetMapping("/rules")
    public ApiResponse<RiteDtos.Rules> rules() {
        return new ApiResponse<>(200, "ok.common.sent", rules.get(resolver.current().organizationId()));
    }

    @PutMapping("/rules")
    public ApiResponse<RiteDtos.Rules> saveRules(@RequestBody RiteDtos.RulesRequest req) {
        return new ApiResponse<>(200, "ok.rite.rulesSaved", rules.update(resolver.actor(), resolver.current(), req));
    }

    private RiteDtos.RequirementRequest withType(RiteDtos.RequirementRequest r) {
        if (r == null) {
            return null;
        }
        return new RiteDtos.RequirementRequest(type, r.code(), r.label(), r.required(), r.source(), r.minAge(), r.active(), r.sortOrder(), r.version());
    }
}
