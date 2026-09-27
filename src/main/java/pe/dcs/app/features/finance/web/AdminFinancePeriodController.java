package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.FiscalPeriodService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/** M15 · Periodos fiscales (FIN_MOVEMENTS): se abren solos; K cierra, Z reabre con motivo (solo ORG_ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/finance/periods")
@RequiredArgsConstructor
public class AdminFinancePeriodController {

    private static final String MODULE = FinanceSupport.MOD_MOVEMENTS;

    private final FiscalPeriodService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<FinanceDtos.FiscalPeriodView>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.current()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.FiscalPeriodView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping("/{id}/close")
    @ModuleAccess(module = MODULE, action = Action.K)
    public ApiResponse<FinanceDtos.FiscalPeriodView> close(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.finance.periodClosed", service.close(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/reopen")
    @ModuleAccess(module = MODULE, action = Action.Z)
    public ApiResponse<FinanceDtos.FiscalPeriodView> reopen(@PathVariable UUID id, @RequestBody(required = false) FinanceDtos.ReopenRequest req) {
        return new ApiResponse<>(200, "ok.finance.periodReopened", service.reopen(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }
}
