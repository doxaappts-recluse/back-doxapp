package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FundService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.Map;
import java.util.UUID;

/** M15 · Fondos (FIN_FUNDS, N2 solo). */
@RestController
@RequestMapping("/api/v1/admin/finance/funds")
@RequiredArgsConstructor
public class AdminFinanceFundController {

    private static final String MODULE = "FIN_FUNDS";

    private final FundService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.FundView>> search(@RequestBody(required = false) FinanceDtos.FundSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.FundView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.FundView> create(@RequestBody FinanceDtos.FundRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.FundView> update(@PathVariable UUID id, @RequestBody FinanceDtos.FundRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @PutMapping("/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.FundView> status(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return new ApiResponse<>(200, "ok.common.statusChanged", service.setStatus(resolver.actor(), resolver.current(), id, body.get("status")));
    }
}
