package pe.dcs.app.features.event.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.event.service.EventRegistrationService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

/** M14 · Una inscripción por id: detalle, pago (provisional hasta M15) y cancelación. El alta va por /admin/events/{id}/registrations. */
@RestController
@RequestMapping("/api/v1/admin/event-registrations")
@RequiredArgsConstructor
public class AdminEventRegistrationController {

    private static final String MODULE = "EVENTS";

    private final EventRegistrationService service;
    private final AccessScopeResolver resolver;

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<EventDtos.RegistrationDetail> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<EventDtos.RegistrationDetail> cancel(@PathVariable UUID id, @RequestBody(required = false) EventDtos.CancelRequest req) {
        return new ApiResponse<>(200, "ok.event.unregistered", service.cancelRegistration(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/payment")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<EventDtos.RegistrationDetail> submitPayment(@PathVariable UUID id, @RequestBody EventDtos.PaymentRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.submitPayment(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/payment/confirm")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<EventDtos.RegistrationDetail> confirmPayment(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.confirmPayment(resolver.actor(), resolver.current(), id));
    }
}
