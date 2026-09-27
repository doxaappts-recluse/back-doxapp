package pe.dcs.app.features.transfer.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.transfer.dto.TransferDtos;
import pe.dcs.app.features.transfer.service.BranchTransferService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M21 · Traslados de sede (N2/N3, módulo contratable): V consultar, C solicitar, A ejecutar, E forzar (solo ORG_ADMIN). La aprobación va por /admin/approvals. */
@RestController
@RequestMapping("/api/v1/admin/branch-transfers")
@RequiredArgsConstructor
public class AdminBranchTransferController {

    private static final String MODULE = "BRANCH_TRANSFER";

    private final BranchTransferService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<TransferDtos.Summary>> search(@RequestBody(required = false) TransferDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/destinations")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<java.util.List<TransferDtos.Destination>> destinations() {
        return new ApiResponse<>(200, "ok.common.sent", service.destinations(resolver.current()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TransferDtos.Summary> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.actor(), resolver.current(), id));
    }

    @GetMapping("/{id}/preview")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<TransferDtos.Preview> preview(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.preview(resolver.actor(), resolver.current(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TransferDtos.Summary> create(@RequestBody TransferDtos.CreateRequest req) {
        return new ApiResponse<>(201, "ok.transfer.requested", service.create(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/force")
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<TransferDtos.Summary> force(@RequestBody TransferDtos.CreateRequest req) {
        return new ApiResponse<>(201, "ok.transfer.approved", service.force(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/{id}/execute")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<TransferDtos.Summary> execute(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.transfer.executed", service.execute(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<TransferDtos.Summary> cancel(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.approval.cancelled", service.cancel(resolver.actor(), resolver.current(), id));
    }
}
