package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FinanceRulesService;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M15 · Reglas de finanzas de la organización (solo ORG_ADMIN, vía FIN_MOVEMENTS). */
@RestController
@RequestMapping("/api/v1/admin/finance/rules")
@RequiredArgsConstructor
public class AdminFinanceRulesController {

    private static final String MODULE = FinanceSupport.MOD_MOVEMENTS;

    private final FinanceRulesService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.FinanceRulesView> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.getView(resolver.current()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.FinanceRulesView> update(@RequestBody FinanceDtos.FinanceRulesRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), req));
    }
}
