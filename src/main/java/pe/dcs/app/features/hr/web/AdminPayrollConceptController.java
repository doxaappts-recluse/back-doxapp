package pe.dcs.app.features.hr.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.hr.dto.HrDtos;
import pe.dcs.app.features.hr.service.HrSupport;
import pe.dcs.app.features.hr.service.PayrollConceptService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M17 · Conceptos de planilla (HR_PAYROLL, org). */
@RestController
@RequestMapping("/api/v1/admin/hr/payroll-concepts")
@RequiredArgsConstructor
public class AdminPayrollConceptController {

    private static final String MODULE = HrSupport.MOD_PAYROLL;

    private final PayrollConceptService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<HrDtos.ConceptView>> list(@RequestParam(required = false) Boolean activeOnly) {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current(), activeOnly));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<HrDtos.ConceptView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<HrDtos.ConceptView> create(@RequestBody HrDtos.ConceptRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<HrDtos.ConceptView> update(@PathVariable UUID id, @RequestBody HrDtos.ConceptRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }
}
