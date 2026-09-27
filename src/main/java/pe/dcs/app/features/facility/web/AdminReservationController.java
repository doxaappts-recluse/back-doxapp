package pe.dcs.app.features.facility.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.facility.dto.FacilityDtos;
import pe.dcs.app.features.facility.service.FacilitySupport;
import pe.dcs.app.features.facility.service.ReservationService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.time.LocalDate;
import java.util.UUID;

/** M16 · Reservas de espacios (SPACES). */
@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private static final String MODULE = FacilitySupport.MOD_SPACES;

    private final ReservationService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FacilityDtos.ReservationView>> search(@RequestBody(required = false) FacilityDtos.ReservationSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.ReservationView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @GetMapping("/availability")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FacilityDtos.AvailabilityView> availability(@RequestParam UUID spaceId, @RequestParam LocalDate date) {
        return new ApiResponse<>(200, "ok.common.sent", service.availability(resolver.current(), spaceId, date));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FacilityDtos.ReservationSubmitResult> submit(@RequestBody FacilityDtos.ReservationRequest req) {
        FacilityDtos.ReservationSubmitResult out = service.submit(resolver.actor(), resolver.current(), req);
        String msg = out.preview() ? "ok.reservation.preview" : "ok.reservation.created";
        return new ApiResponse<>(201, msg, out);
    }

    @PostMapping("/{id}/approve")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<FacilityDtos.ReservationView> approve(@PathVariable UUID id, @RequestBody(required = false) FacilityDtos.ReservationDecisionRequest req) {
        service.approve(resolver.actor(), resolver.current(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.reservation.approved", service.get(resolver.current(), id));
    }

    @PostMapping("/{id}/reject")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<FacilityDtos.ReservationView> reject(@PathVariable UUID id, @RequestBody(required = false) FacilityDtos.ReservationDecisionRequest req) {
        service.reject(resolver.actor(), resolver.current(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.reservation.rejected", service.get(resolver.current(), id));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<FacilityDtos.ReservationView> cancel(@PathVariable UUID id, @RequestBody(required = false) FacilityDtos.ReservationDecisionRequest req) {
        service.cancel(resolver.actor(), resolver.current(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.reservation.cancelled", service.get(resolver.current(), id));
    }
}
