package pe.dcs.app.features.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.features.hr.service.HrSupport;
import pe.dcs.app.features.hr.service.LeaveRequestService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M17 · Vacaciones y permisos (HR_LEAVE). */
@RestController
@RequiredArgsConstructor
public class AdminLeaveRequestController {

    private static final String MODULE = HrSupport.MOD_LEAVE;

    private final LeaveRequestService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/api/v1/admin/hr/leave-requests/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<HrDtos.LeaveView>> search(@RequestBody(required = false) HrDtos.LeaveSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/api/v1/admin/hr/leave-requests/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<HrDtos.LeaveView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping("/api/v1/admin/hr/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<HrDtos.LeaveView> submit(@RequestBody HrDtos.LeaveSubmitRequest req) {
        return new ApiResponse<>(201, "ok.hr.leaveRequested", service.submit(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/api/v1/admin/hr/leave-requests/{id}/approve")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<HrDtos.LeaveView> approve(@PathVariable UUID id, @RequestBody(required = false) HrDtos.LeaveDecisionRequest req) {
        service.approve(resolver.actor(), resolver.current(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.hr.leaveApproved", service.get(resolver.current(), id));
    }

    @PostMapping("/api/v1/admin/hr/leave-requests/{id}/reject")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<HrDtos.LeaveView> reject(@PathVariable UUID id, @RequestBody(required = false) HrDtos.LeaveDecisionRequest req) {
        service.reject(resolver.actor(), resolver.current(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.hr.leaveRejected", service.get(resolver.current(), id));
    }

    @PostMapping("/api/v1/admin/hr/leave-requests/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<HrDtos.LeaveView> cancel(@PathVariable UUID id, @RequestBody(required = false) HrDtos.LeaveDecisionRequest req) {
        service.cancel(resolver.actor(), resolver.current(), id, req == null ? null : req.reason());
        return new ApiResponse<>(200, "ok.hr.leaveCancelled", service.get(resolver.current(), id));
    }

    @GetMapping("/api/v1/admin/hr/leave-balances")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<java.util.List<HrDtos.LeaveBalanceView>> balances(@RequestParam UUID staffId, @RequestParam(required = false) Integer year) {
        return new ApiResponse<>(200, "ok.common.sent", service.balances(resolver.current(), staffId, year));
    }

    @PutMapping("/api/v1/admin/hr/leave-balances/{staffId}/{year}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HrDtos.LeaveBalanceView> adjustBalance(@PathVariable UUID staffId, @PathVariable int year, @RequestBody HrDtos.LeaveBalanceAdjustRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.adjustBalance(resolver.actor(), resolver.current(), staffId, year, req));
    }
}
