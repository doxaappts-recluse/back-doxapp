package pe.dcs.app.features.event.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.event.service.EventRegistrationService;
import pe.dcs.app.features.event.service.EventService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** M14 · Eventos: alta, ciclo de vida, tarifas, inscritos, check-in y lista de espera. */
@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private static final String MODULE = "EVENTS";

    private final EventService service;
    private final EventRegistrationService registrations;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<EventDtos.EventSummary>> search(@RequestBody(required = false) EventDtos.EventSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<EventDtos.EventDetail> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<EventDtos.EventDetail> create(@RequestBody EventDtos.EventRequest req) {
        return new ApiResponse<>(201, "ok.event.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<EventDtos.EventDetail> update(@PathVariable UUID id, @RequestBody EventDtos.EventRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.sent", null);
    }

    @PutMapping("/{id}/price-tiers")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<EventDtos.EventDetail> priceTiers(@PathVariable UUID id, @RequestBody List<EventDtos.PriceTier> req) {
        return new ApiResponse<>(200, "ok.common.sent", service.setPriceTiers(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/publish")
    @ModuleAccess(module = MODULE, action = Action.P)
    public ApiResponse<EventDtos.EventDetail> publish(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.event.published", service.publish(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<EventDtos.EventDetail> cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return new ApiResponse<>(200, "ok.event.cancelled", service.cancel(resolver.actor(), resolver.current(), id, body == null ? null : body.get("reason")));
    }

    @PostMapping("/{id}/finish")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<EventDtos.EventDetail> finish(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.event.finished", service.finish(resolver.actor(), resolver.current(), id));
    }

    // ---------------------------------------------------------------- inscritos

    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<EventDtos.RegistrationDetail> register(@PathVariable UUID id, @RequestBody EventDtos.RegisterRequest req) {
        EventDtos.RegistrationDetail out = registrations.register(resolver.actor(), resolver.current(), id, req);
        String msg = "WAITLISTED".equals(out.summary().status()) ? "ok.event.waitlisted" : "ok.event.registered";
        return new ApiResponse<>(201, msg, out);
    }

    @PostMapping("/{id}/registrations/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<EventDtos.RegistrationSummary>> registrationsSearch(@PathVariable UUID id, @RequestBody(required = false) EventDtos.RegistrationSearch req) {
        EventDtos.RegistrationSearch effective = new EventDtos.RegistrationSearch(
                new EventDtos.RegistrationSearch.RegistrationFilters(id, req == null || req.filters() == null ? null : req.filters().personId(),
                        req == null || req.filters() == null ? null : req.filters().status(), req == null || req.filters() == null ? null : req.filters().paymentStatus()),
                req == null ? null : req.pagination(), req == null ? null : req.sorts());
        return new ApiResponse<>(200, "ok.common.sent", registrations.search(resolver.current(), effective));
    }

    @PostMapping("/{id}/checkin")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<EventDtos.CheckinResult> checkin(@PathVariable UUID id, @RequestBody EventDtos.CheckinRequest req) {
        return new ApiResponse<>(200, "ok.event.checkedIn", registrations.checkin(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/waitlist/promote")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<EventDtos.WaitlistPromoteResult> promote(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.event.promoted", registrations.promoteWaitlist(resolver.actor(), resolver.current(), id));
    }
}
