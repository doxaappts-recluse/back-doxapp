package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.BudgetService;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M15 · Presupuestos (FIN_BUDGETS): DRAFT → APPROVED (bloquea edición); la ejecución va incluida en la propia respuesta. */
@RestController
@RequestMapping("/api/v1/admin/finance/budgets")
@RequiredArgsConstructor
public class AdminFinanceBudgetController {

    private static final String MODULE = FinanceSupport.MOD_BUDGETS;

    private final BudgetService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.BudgetView>> search(@RequestBody(required = false) FinanceDtos.BudgetSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.BudgetView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.BudgetView> create(@RequestBody FinanceDtos.BudgetRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.BudgetView> update(@PathVariable UUID id, @RequestBody FinanceDtos.BudgetRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/approve")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<FinanceDtos.BudgetView> approve(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.approved", service.approve(resolver.actor(), resolver.current(), id));
    }
}
