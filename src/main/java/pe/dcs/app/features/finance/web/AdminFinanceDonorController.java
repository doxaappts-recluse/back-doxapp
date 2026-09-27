package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.DonorService;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M15 · Donantes (FIN_DONORS). */
@RestController
@RequestMapping("/api/v1/admin/finance/donors")
@RequiredArgsConstructor
public class AdminFinanceDonorController {

    private static final String MODULE = FinanceSupport.MOD_DONORS;

    private final DonorService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.DonorView>> search(@RequestBody(required = false) FinanceDtos.DonorSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.DonorView> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.DonorView> create(@RequestBody FinanceDtos.DonorRequest req) {
        return new ApiResponse<>(201, "ok.common.created", service.create(resolver.actor(), resolver.current(), req));
    }

    @PutMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.DonorView> update(@PathVariable UUID id, @RequestBody FinanceDtos.DonorRequest req) {
        return new ApiResponse<>(200, "ok.common.updated", service.update(resolver.actor(), resolver.current(), id, req));
    }

    @DeleteMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.D)
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(resolver.actor(), resolver.current(), id);
        return new ApiResponse<>(200, "ok.common.deleted", null);
    }
}
