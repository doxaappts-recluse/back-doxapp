package pe.dcs.app.features.pastoral.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.pastoral.dto.PastoralDtos;
import pe.dcs.app.features.pastoral.service.PrayerService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M12 · Oración (N2/N3, contratable): V consultar/muro, C registrar/apoyar, E moderar/responder. */
@RestController
@RequestMapping("/api/v1/admin/prayer-requests")
@RequiredArgsConstructor
public class AdminPrayerController {

    private static final String MODULE = "PRAYER";

    private final PrayerService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<PastoralDtos.PrayerSummary>> search(@RequestBody(required = false) PastoralDtos.PrayerSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    /** Muro público de oración de la organización (peticiones aprobadas y no anónimas cuando corresponde) [V12]. */
    @GetMapping("/wall")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<PastoralDtos.WallItem>> wall(@RequestParam(defaultValue = "50") int limit) {
        return new ApiResponse<>(200, "ok.common.sent", service.wall(resolver.current(), limit));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PastoralDtos.PrayerResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<PastoralDtos.PrayerResponse> create(@RequestBody PastoralDtos.PrayerCreateRequest req) {
        return new ApiResponse<>(201, "ok.prayer.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}/moderate")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PastoralDtos.PrayerResponse> moderate(@PathVariable UUID id, @RequestBody PastoralDtos.ModerateRequest req) {
        return new ApiResponse<>(200, "ok.prayer.moderated", service.moderate(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/support")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<Long> support(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.prayer.supported", service.support(resolver.actor(), resolver.current(), id));
    }

    @PutMapping("/{id}/answer")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<PastoralDtos.PrayerResponse> answer(@PathVariable UUID id, @RequestBody PastoralDtos.AnswerRequest req) {
        return new ApiResponse<>(200, "ok.prayer.answered", service.answer(resolver.actor(), resolver.current(), id, req));
    }
}
