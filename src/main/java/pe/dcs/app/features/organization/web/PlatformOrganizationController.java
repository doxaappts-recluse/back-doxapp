package pe.dcs.app.features.organization.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.organization.domain.OrganizationStatus;
import pe.dcs.app.features.organization.dto.OrgCreateRequest;
import pe.dcs.app.features.organization.dto.OrgResponse;
import pe.dcs.app.features.organization.dto.OrgSearchRequest;
import pe.dcs.app.features.organization.dto.OrgSlugRequest;
import pe.dcs.app.features.organization.dto.OrgStatusRequest;
import pe.dcs.app.features.organization.dto.OrgUpdateRequest;
import pe.dcs.app.features.organization.dto.SlugCheckResponse;
import pe.dcs.app.features.organization.service.OrganizationService;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/**
 * M02 · Organizaciones (N1). SYSTEM_ADMIN: V C E S · SYSTEM_SUPPORT: solo V. La autorización la resuelve
 * {@code ModuleAccessInterceptor}. Cambiar el slug y el estado se consideran gestión de estado (S).
 */
@RestController
@RequestMapping("/api/v1/platform/organizations")
@RequiredArgsConstructor
public class PlatformOrganizationController {

    private static final String MODULE = "ORGANIZATIONS";

    private final OrganizationService service;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<OrgResponse>> search(@RequestBody(required = false) OrgSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(req));
    }

    /** Disponibilidad de un identificador (para el paso "Identificador" del alta y el cambio de slug). */
    @GetMapping("/slug-available")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<SlugCheckResponse> slugAvailable(@RequestParam String slug, @RequestParam(required = false) UUID excludeId) {
        return new ApiResponse<>(200, "ok.common.sent", service.checkSlug(slug, excludeId));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<OrgResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<OrgResponse> create(@Valid @RequestBody OrgCreateRequest req) {
        return new ApiResponse<>(201, "ok.org.created", service.create(req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<OrgResponse> update(@PathVariable UUID id, @Valid @RequestBody OrgUpdateRequest req) {
        return new ApiResponse<>(200, "ok.org.updated", service.update(id, req));
    }

    @PutMapping("/{id}/slug")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<OrgResponse> changeSlug(@PathVariable UUID id, @Valid @RequestBody OrgSlugRequest req) {
        return new ApiResponse<>(200, "ok.org.slugChanged", service.changeSlug(id, req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<OrgResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody OrgStatusRequest req) {
        OrganizationStatus from = service.get(id).status();
        OrgResponse res = service.changeStatus(id, req);
        String key = switch (res.status()) {
            case TRIAL -> "ok.org.trialStarted";
            case ACTIVE -> from == OrganizationStatus.SUSPENDED ? "ok.org.reactivated" : "ok.org.activated";
            case SUSPENDED -> "ok.org.suspended";
            case CLOSED -> "ok.org.closed";
            default -> "ok.common.statusChanged";
        };
        return new ApiResponse<>(200, key, res);
    }
}
