package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.PledgeService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.Map;
import java.util.UUID;

/** M15 · Promesas de ofrenda (FIN_DONORS): ACTIVE → FULFILLED/CANCELLED manual. */
@RestController
@RequestMapping("/api/v1/admin/finance/pledges")
@RequiredArgsConstructor
public class AdminFinancePledgeController {

    private static final String MODULE = FinanceSupport.MOD_DONORS;

    private final PledgeService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.PledgeView>> search(@RequestBody(required = false) FinanceDtos.PledgeSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.PledgeView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.PledgeView> create(@RequestBody FinanceDtos.PledgeRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.PledgeView> status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return new ApiResponse<>(200, "ok.common.statusChanged", service.setStatus(resolver.actor(), resolver.current(), id, body.get("status")));
    }
}
