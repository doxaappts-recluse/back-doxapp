package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.OfferingCountService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M15 · Conteo de ofrenda (FIN_MOVEMENTS): dos contadores, doble confirmación. */
@RestController
@RequestMapping("/api/v1/admin/finance/offering-counts")
@RequiredArgsConstructor
public class AdminFinanceOfferingCountController {

    private static final String MODULE = FinanceSupport.MOD_MOVEMENTS;

    private final OfferingCountService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.OfferingCountView>> search(@RequestBody(required = false) FinanceDtos.OfferingCountSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.OfferingCountView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.OfferingCountView> create(@RequestBody FinanceDtos.OfferingCountRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/confirm")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<FinanceDtos.OfferingCountView> confirm(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.finance.countConfirmed", service.confirm(resolver.actor(), resolver.current(), id));
    }
}
