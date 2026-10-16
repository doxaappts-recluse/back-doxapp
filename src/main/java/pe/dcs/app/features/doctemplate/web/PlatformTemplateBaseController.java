package pe.dcs.app.features.doctemplate.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.doctemplate.dto.TemplateDtos;
import pe.dcs.app.features.doctemplate.service.TemplateBaseService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M18 · Plantillas base (N1) — catálogo de la plataforma, gestión de SYSTEM_ADMIN vía el módulo CATALOGS (mismo patrón que {@code PlatformCatalogController}). */
@RestController
@RequestMapping("/api/v1/platform/template-bases")
@RequiredArgsConstructor
public class PlatformTemplateBaseController {

    private static final String MODULE = "CATALOGS";

    private final TemplateBaseService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private void staff() {
        guard.staffActor(resolver.actor());
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<TemplateDtos.BaseTemplateView>> list(@RequestParam(required = false) String type) {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", service.list(type));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TemplateDtos.BaseTemplateView> get(@PathVariable UUID id) {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TemplateDtos.BaseTemplateView> create(@RequestBody TemplateDtos.BaseTemplateRequest req) {
        staff();
        return new ApiResponse<>(201, "ok.template.baseSaved", service.create(resolver.actor(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TemplateDtos.BaseTemplateView> update(@PathVariable UUID id, @RequestBody TemplateDtos.BaseTemplateRequest req) {
        staff();
        return new ApiResponse<>(200, "ok.template.baseSaved", service.update(resolver.actor(), id, req));
    }

    @PatchMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<TemplateDtos.BaseTemplateView> status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        staff();
        return new ApiResponse<>(200, "ok.template.baseSaved", service.setStatus(resolver.actor(), id, body.get("status")));
    }
}
