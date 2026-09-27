package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.SpaceService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M16 · Espacios (SPACES). */
@RestController
@RequestMapping("/api/v1/admin/spaces")
@RequiredArgsConstructor
public class AdminSpaceController {

    private static final String MODULE = FacilitySupport.MOD_SPACES;

    private final SpaceService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FacilityDtos.SpaceView>> search(@RequestBody(required = false) FacilityDtos.SpaceSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/options")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<FacilityDtos.SpaceView>> options(@RequestParam UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", service.activeOptions(resolver.current(), branchId));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.SpaceView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FacilityDtos.SpaceView> create(@RequestBody FacilityDtos.SpaceRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FacilityDtos.SpaceView> update(@PathVariable UUID id, @RequestBody FacilityDtos.SpaceRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<FacilityDtos.SpaceView> status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return new ApiResponse<>(200, "ok.common.statusChanged", service.setStatus(resolver.actor(), resolver.current(), id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }
}
