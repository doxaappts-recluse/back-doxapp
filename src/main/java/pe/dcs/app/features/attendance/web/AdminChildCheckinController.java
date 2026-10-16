package pe.dcs.app.features.attendance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.attendance.dto.AttendanceDtos;
import pe.dcs.app.features.attendance.service.ChildCheckinService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M09 · Check-in de niños (módulo contratable CHILD_CHECKIN): V consultar, T registrar entrada y entrega, H ver alergias en la etiqueta. */
@RestController
@RequestMapping("/api/v1/admin/child-checkin")
@RequiredArgsConstructor
public class AdminChildCheckinController {

    private static final String MODULE = "CHILD_CHECKIN";

    private final ChildCheckinService service;
    private final AccessScopeResolver resolver;

    @GetMapping("/open-sessions")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<AttendanceDtos.SessionSummary>> openSessions() {
        return new ApiResponse<>(200, "ok.common.sent", service.openSessions(resolver.current()));
    }

    @PostMapping("/children/search")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<List<AttendanceDtos.ChildCandidate>> children(@RequestBody AttendanceDtos.ChildSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.candidates(resolver.current(), req));
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<AttendanceDtos.CheckinResponse>> list(@RequestParam UUID sessionId, @RequestParam(defaultValue = "false") boolean pending) {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.actor(), resolver.current(), sessionId, pending));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AttendanceDtos.CheckinResponse> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor(), resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<AttendanceDtos.CheckinResponse> checkin(@RequestBody AttendanceDtos.CheckinRequest req) {
        return new ApiResponse<>(201, "ok.checkin.done", service.checkin(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/checkout")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<AttendanceDtos.CheckinResponse> checkout(@PathVariable UUID id, @RequestBody AttendanceDtos.CheckoutRequest req) {
        return new ApiResponse<>(200, "ok.checkin.pickedUp", service.checkout(resolver.actor(), resolver.current(), id, req));
    }
}
