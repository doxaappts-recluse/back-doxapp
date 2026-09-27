package pe.dcs.app.features.catalog.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.catalog.dto.*;
import pe.dcs.app.features.catalog.service.CatalogService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M23 · Catálogos BASE (N1). SYSTEM_ADMIN: V C E S · SYSTEM_SUPPORT: solo V. */
@RestController
@RequestMapping("/api/v1/platform/catalogs")
@RequiredArgsConstructor
public class PlatformCatalogController {

    private static final String MODULE = "CATALOGS";

    private final CatalogService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private void staff() {
        guard.staffActor(resolver.actor());
    }

    @GetMapping("/types")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<CatalogTypeResponse>> types() {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", service.types());
    }

    @GetMapping("/{type}/items")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<CatalogItemResponse>> list(@PathVariable String type, @RequestParam(required = false) UUID parentId) {
        staff();
        return new ApiResponse<>(200, "ok.common.sent", service.listBase(type, parentId));
    }

    @PostMapping("/{type}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<CatalogItemResponse> create(@PathVariable String type, @RequestBody CatalogItemRequest req) {
        staff();
        return new ApiResponse<>(201, "ok.catalog.saved", service.createBase(type, req));
    }

    @PutMapping("/{type}/items/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<CatalogItemResponse> update(@PathVariable String type, @PathVariable UUID id, @RequestBody CatalogItemRequest req) {
        staff();
        return new ApiResponse<>(200, "ok.catalog.saved", service.updateBase(type, id, req));
    }

    @PatchMapping("/{type}/items/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<CatalogItemResponse> status(@PathVariable String type, @PathVariable UUID id, @RequestBody CatalogActiveRequest req) {
        staff();
        CatalogItemResponse r = service.setBaseActive(type, id, req.active());
        return new ApiResponse<>(200, r.active() ? "ok.catalog.saved" : "ok.catalog.deactivated", r);
    }
}
