package pe.dcs.app.features.finance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.finance.dto.FinanceDtos;
import pe.dcs.app.features.finance.service.FinanceSupport;
import pe.dcs.app.features.finance.service.FinancialMovementService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M15 · Movimientos financieros (FIN_MOVEMENTS). La creación es multipart: permite adjuntar el comprobante en el mismo
 * paso [V9] (mismo patrón que M22 al abrir un caso de soporte); {@code /attachments} sigue existiendo aparte para sumar más
 * evidencia a un movimiento ya creado.
 */
@RestController
@RequestMapping("/api/v1/admin/finance/movements")
@RequiredArgsConstructor
public class AdminFinanceMovementController {

    private static final String MODULE = FinanceSupport.MOD_MOVEMENTS;

    private final FinancialMovementService service;
    private final AccessScopeResolver resolver;

    @PostMapping("/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<FinanceDtos.MovementSummary>> search(@RequestBody(required = false) FinanceDtos.MovementSearch req) {
        return new ApiResponse<>(200, "ok.common.sent", service.search(resolver.current(), req));
    }

    @GetMapping("/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<FinanceDtos.MovementDetail> get(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(resolver.current(), id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<FinanceDtos.MovementDetail> create(@RequestParam UUID branchId, @RequestParam LocalDate movementDate, @RequestParam String type,
                                                          @RequestParam String category, @RequestParam UUID fundId,
                                                          @RequestParam(required = false) UUID accountId, @RequestParam String amount,
                                                          @RequestParam String method, @RequestParam(required = false) UUID donorId,
                                                          @RequestParam(required = false) Boolean anonymous, @RequestParam(required = false) String description,
                                                          @RequestParam(required = false) UUID eventId,
                                                          @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        FinanceDtos.MovementRequest req = new FinanceDtos.MovementRequest(branchId, movementDate, type, category, fundId, accountId, amount, method,
                donorId, anonymous, description, eventId);
        return new ApiResponse<>(201, "ok.finance.created", service.create(resolver.actor(), resolver.current(), req, files));
    }

    @PostMapping("/{id}/approve")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<FinanceDtos.MovementDetail> approve(@PathVariable UUID id) {
        FinanceDtos.MovementDetail out = service.approve(resolver.actor(), resolver.current(), id);
        String msg = out.summary().receiptNo() != null ? "ok.finance.receiptIssued" : "ok.finance.approved";
        return new ApiResponse<>(200, msg, out);
    }

    @PostMapping("/{id}/reject")
    @ModuleAccess(module = MODULE, action = Action.A)
    public ApiResponse<FinanceDtos.MovementDetail> reject(@PathVariable UUID id, @RequestBody(required = false) FinanceDtos.DecisionRequest req) {
        return new ApiResponse<>(200, "ok.finance.rejected", service.reject(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/void")
    @ModuleAccess(module = MODULE, action = Action.Y)
    public ApiResponse<FinanceDtos.MovementDetail> voidMovement(@PathVariable UUID id, @RequestBody(required = false) FinanceDtos.DecisionRequest req) {
        return new ApiResponse<>(200, "ok.finance.voided", service.voidMovement(resolver.actor(), resolver.current(), id, req == null ? null : req.reason()));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.MovementDetail> addAttachments(@PathVariable UUID id, @RequestParam("files") List<MultipartFile> files) {
        return new ApiResponse<>(200, "ok.common.updated", service.addAttachments(resolver.actor(), resolver.current(), id, files));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<FinanceDtos.MovementDetail> deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return new ApiResponse<>(200, "ok.common.updated", service.deleteAttachment(resolver.actor(), resolver.current(), id, attachmentId));
    }

    // No existe DELETE de movimientos [error.finance.cannotDelete]: se anulan con POST .../void; a propósito no se expone
    // ningún endpoint DELETE /movements/{id} (así lo pide la especificación de la tarea).
}
