package pe.dcs.app.features.catalog.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.catalog.dto.*;
import pe.dcs.app.features.catalog.service.CatalogService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M23 · Catálogos de la organización (N2/N3). Ver: ORG_ADMIN y ORG_BRANCH_ADMIN. Escribir: solo ORG_ADMIN (la regla
 * está en el servicio porque ORG_BRANCH_ADMIN recibe todas las acciones por rol).
 */
@RestController
@RequestMapping("/api/v1/admin/catalogs")
@RequiredArgsConstructor
public class AdminCatalogController {

    private static final String MODULE = "CATALOGS";

    private final CatalogService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @GetMapping("/types")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<CatalogTypeResponse>> types() {
        actor();
        return new ApiResponse<>(200, "ok.common.sent", service.types());
    }

    @GetMapping("/{type}/items")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<CatalogItemResponse>> list(@PathVariable String type, @RequestParam(required = false) UUID parentId) {
        return new ApiResponse<>(200, "ok.common.sent", service.listOrg(actor(), type, parentId));
    }

    @PostMapping("/{type}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<CatalogItemResponse> create(@PathVariable String type, @RequestBody CatalogItemRequest req) {
        return new ApiResponse<>(201, "ok.catalog.saved", service.createOrg(actor(), type, req));
    }

    @PutMapping("/{type}/items/reorder")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<List<CatalogItemResponse>> reorder(@PathVariable String type, @RequestBody CatalogReorderRequest req) {
        return new ApiResponse<>(200, "ok.catalog.reordered", service.reorder(actor(), type, req.ids()));
    }

    @PutMapping("/{type}/items/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<CatalogItemResponse> update(@PathVariable String type, @PathVariable UUID id, @RequestBody CatalogItemRequest req) {
        return new ApiResponse<>(200, "ok.catalog.saved", service.updateOrg(actor(), type, id, req));
    }

    @PatchMapping("/{type}/items/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<CatalogItemResponse> status(@PathVariable String type, @PathVariable UUID id, @RequestBody CatalogActiveRequest req) {
        CatalogItemResponse r = service.setOrgActive(actor(), type, id, req.active());
        return new ApiResponse<>(200, r.active() ? "ok.catalog.saved" : "ok.catalog.deactivated", r);
    }

    @PostMapping("/{type}/items/{id}/hide")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<CatalogItemResponse> hide(@PathVariable String type, @PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.catalog.deactivated", service.setHidden(actor(), type, id, true));
    }

    @PostMapping("/{type}/items/{id}/show")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<CatalogItemResponse> show(@PathVariable String type, @PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.catalog.saved", service.setHidden(actor(), type, id, false));
    }

    @DeleteMapping("/{type}/items/{id}")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<Void> delete(@PathVariable String type, @PathVariable UUID id) {
        service.deleteOrg(actor(), type, id);
        return new ApiResponse<>(200, "ok.catalog.deleted", null);
    }
}
