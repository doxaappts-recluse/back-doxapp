package pe.dcs.app.features.announcement.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.announcement.dto.*;
import pe.dcs.app.features.announcement.service.AnnouncementService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M22 · anuncios de plataforma (N1). SYSTEM_ADMIN crea, edita borradores, publica y cancela; SYSTEM_SUPPORT solo consulta. */
@RestController
@RequestMapping("/api/v1/platform/announcements")
@RequiredArgsConstructor
public class PlatformAnnouncementController {

    private static final String MODULE = "PLATFORM_ANNOUNCEMENTS";

    private final AnnouncementService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.staffActor(resolver.actor());
    }

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<AnnouncementResponse>> search(@RequestBody(required = false) AnnouncementSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(actor(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AnnouncementResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(actor(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AnnouncementResponse> create(@RequestBody AnnouncementRequest req) {
        return new ApiResponse<>(201, "ok.announcement.saved", service.create(actor(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<AnnouncementResponse> update(@PathVariable UUID id, @RequestBody AnnouncementRequest req) {
        return new ApiResponse<>(200, "ok.announcement.saved", service.update(actor(), id, req));
    }

    @PostMapping("/{id}/publish")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<AnnouncementResponse> publish(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.announcement.published", service.publish(actor(), id));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<AnnouncementResponse> cancel(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.announcement.cancelled", service.cancel(actor(), id));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(actor(), id);
        return new ApiResponse<>(200, "ok.announcement.deleted", null);
    }
}
