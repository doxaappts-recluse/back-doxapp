package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.FinancialMovementService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/** M15 · Consolidado [T14]: ORG_ADMIN ve todas las sedes, ORG_BRANCH_ADMIN solo la suya (el propio alcance ya lo filtra). */
@RestController
@RequestMapping("/api/v1/admin/finance/consolidated")
@RequiredArgsConstructor
public class AdminFinanceConsolidatedController {

    private static final String MODULE = FinanceSupport.MOD_MOVEMENTS;

    private final FinancialMovementService service;
    private final AccessScopeResolver resolver;

    @PostMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.ConsolidatedResponse> consolidated(@RequestBody(required = false) FinanceDtos.ConsolidatedRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.consolidated(resolver.current(), req));
    }
}
