package pe.dcs.app.features.approval.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.approval.dto.ApprovalDtos;
import pe.dcs.app.features.approval.service.ApprovalEngine;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M21 · Bandeja única de solicitudes (N2/N3). Todo se consulta con APPROVAL_INBOX V; decidir exige además la acción A del módulo dueño
 * del tipo de solicitud, que comprueba el motor (no este controlador).
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {

    private static final String MODULE = "APPROVAL_INBOX";

    private final ApprovalEngine engine;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<ApprovalDtos.Summary>> search(@RequestBody(required = false) ApprovalDtos.Search req) {
        return new ApiResponse<>(200, "ok.common.sent", engine.search(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/types")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<ApprovalDtos.TypeInfo>> types() {
        return new ApiResponse<>(200, "ok.common.sent", engine.types(resolver.actor()));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ApprovalDtos.Detail> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", engine.get(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/{id}/approve")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ApprovalDtos.Summary> approve(@PathVariable UUID id, @RequestBody(required = false) ApprovalDtos.DecisionRequest req) {
        return new ApiResponse<>(200, "ok.approval.approved", engine.approve(resolver.actor(), resolver.current(), id, req == null ? null : req.note()));
    }

    @PostMapping("/{id}/reject")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ApprovalDtos.Summary> reject(@PathVariable UUID id, @RequestBody(required = false) ApprovalDtos.RejectRequest req) {
        return new ApiResponse<>(200, "ok.approval.rejected", engine.reject(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/comment")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ApprovalDtos.Detail> comment(@PathVariable UUID id, @RequestBody ApprovalDtos.CommentRequest req) {
        return new ApiResponse<>(200, "ok.approval.commented", engine.comment(resolver.actor(), resolver.current(), id, req));
    }

    @PostMapping("/{id}/cancel")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ApprovalDtos.Summary> cancel(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.approval.cancelled", engine.cancel(resolver.actor(), resolver.current(), id));
    }

    @PostMapping("/bulk")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<ApprovalDtos.BulkResult> bulk(@RequestBody ApprovalDtos.BulkRequest req) {
        return new ApiResponse<>(200, "ok.approval.bulk", engine.bulk(resolver.actor(), resolver.current(), req));
    }
}
