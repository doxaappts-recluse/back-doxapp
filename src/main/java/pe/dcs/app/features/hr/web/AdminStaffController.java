package pe.dcs.app.features.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.features.hr.service.HrSupport;
import pe.dcs.app.features.hr.service.StaffService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/** M17 · Personal (HR_STAFF). */
@RestController
@RequestMapping("/api/v1/admin/hr/staff")
@RequiredArgsConstructor
public class AdminStaffController {

    private static final String MODULE = HrSupport.MOD_STAFF;

    private final StaffService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<HrDtos.StaffView>> search(@RequestBody(required = false) HrDtos.StaffSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/options")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<HrDtos.StaffView>> options(@RequestParam UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", service.activeOptions(resolver.actor(), resolver.current(), branchId));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<HrDtos.StaffView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor(), resolver.current(), id));
    }

    @GetMapping("/{id}/salary-history")
    @ModuleAccess(module = MODULE, action = Action.H)
    public ApiResponse<List<HrDtos.SalaryHistoryView>> salaryHistory(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.salaryHistory(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<HrDtos.StaffView> create(@RequestBody HrDtos.StaffRequest req) {
        return new ApiResponse<>(201, "ok.hr.staffCreated", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HrDtos.StaffView> update(@PathVariable UUID id, @RequestBody HrDtos.StaffRequest req) {
        return new ApiResponse<>(200, "ok.hr.staffUpdated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/salary")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HrDtos.StaffView> salary(@PathVariable UUID id, @RequestBody HrDtos.SalaryChangeRequest req) {
        return new ApiResponse<>(200, "ok.hr.staffUpdated", service.changeSalary(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/terminate")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<HrDtos.StaffView> terminate(@PathVariable UUID id, @RequestBody(required = false) HrDtos.TerminateRequest req) {
        return new ApiResponse<>(200, "ok.hr.staffTerminated", service.terminate(resolver.actor(), resolver.current(), id, req));
    }
}
