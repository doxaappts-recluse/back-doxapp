package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.CashRegisterService;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M15 · Caja diaria (FIN_MOVEMENTS): abrir, movimientos en efectivo enlazados, cerrar con conteo. */
@RestController
@RequestMapping("/api/v1/admin/finance/cash-registers")
@RequiredArgsConstructor
public class AdminFinanceCashRegisterController {

    private static final String MODULE = FinanceSupport.MOD_MOVEMENTS;

    private final CashRegisterService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.CashRegisterView>> search(@RequestBody(required = false) FinanceDtos.CashRegisterSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.CashRegisterView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.CashRegisterView> open(@RequestBody FinanceDtos.CashRegisterOpenRequest req) {
        return new ApiResponse<>(201, "ok.finance.registerOpened", service.open(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/close")
    @ModuleAccess(module = MODULE, action = Action.K)
    public ApiResponse<FinanceDtos.CashRegisterView> close(@PathVariable UUID id, @RequestBody(required = false) FinanceDtos.CashRegisterCloseRequest req) {
        return new ApiResponse<>(200, "ok.finance.registerClosed", service.close(resolver.actor(), resolver.current(), id, req));
    }
}
