package pe.dcs.app.features.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.features.hr.service.HrSupport;
import pe.dcs.app.features.hr.service.PayrollRunService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M17 · Corridas de planilla (HR_PAYROLL). */
@RestController
@RequestMapping("/api/v1/admin/hr/payroll-runs")
@RequiredArgsConstructor
public class AdminPayrollRunController {

    private static final String MODULE = HrSupport.MOD_PAYROLL;

    private final PayrollRunService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<HrDtos.RunView>> search(@RequestBody(required = false) HrDtos.RunSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<HrDtos.RunView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @GetMapping("/{id}/records")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<HrDtos.RecordView>> records(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.records(resolver.actor(), resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<HrDtos.RunView> create(@RequestBody HrDtos.RunCreateRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/calculate")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HrDtos.RunView> calculate(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.hr.payrollCalculated", service.calculate(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/approve")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<HrDtos.RunView> approve(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.hr.payrollApproved", service.approve(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/pay")
    @ModuleAccess(module = MODULE, action = Action.P)
    public ApiResponse<HrDtos.RunView> pay(@PathVariable UUID id, @RequestBody(required = false) HrDtos.RunPayRequest req) {
        return new ApiResponse<>(200, "ok.hr.payrollPaid", service.pay(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/close")
    @ModuleAccess(module = MODULE, action = Action.K)
    public ApiResponse<HrDtos.RunView> close(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.hr.payrollClosed", service.close(resolver.actor(), resolver.current(), id));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.Y)
    public ApiResponse<Void> voidRun(@PathVariable UUID id) {
        service.voidRun(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }
}
