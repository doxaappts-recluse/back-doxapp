package pe.dcs.app.features.support.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.support.dto.*;
import pe.dcs.app.features.support.service.SupportService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.pagination.PageResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * M22 · casos de soporte para el personal de plataforma. SYSTEM_ADMIN y SYSTEM_SUPPORT ven, responden, asignan y cierran
 * (SUPPORT: V C E S T). El mantenimiento manual (alertas de SLA y cierre automático) es solo de SYSTEM_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/platform/support")
@RequiredArgsConstructor
public class PlatformSupportController {

    private static final String MODULE = "SUPPORT";

    private final SupportService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.staffActor(resolver.actor());
    }

    @PostMapping("/cases/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<StaffCaseResponse>> search(@RequestBody(required = false) CaseSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.searchStaff(actor(), req));
    }

    @GetMapping("/cases/summary")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<StaffSummary> summary() {
        return new ApiResponse<>(200, "ok.common.sent", service.summaryStaff(actor()));
    }

    @GetMapping("/assignees")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<AssigneeOption>> assignees() {
        return new ApiResponse<>(200, "ok.common.sent", service.assignees(actor()));
    }

    @GetMapping("/cases/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<StaffCaseResponse> detail(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.detailStaff(actor(), id));
    }

    @PostMapping(value = "/cases/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<StaffCaseResponse> reply(@PathVariable UUID id,
                                                @RequestParam(value = "body", required = false) String body,
                                                @RequestParam(value = "internal", defaultValue = "false") boolean internal,
                                                @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return new ApiResponse<>(200, "ok.support.replied", service.replyStaff(actor(), id, body, internal, files));
    }

    @PutMapping("/cases/{id}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<StaffCaseResponse> update(@PathVariable UUID id, @RequestBody CaseUpdateRequest req) {
        return new ApiResponse<>(200, "ok.support.updated", service.update(actor(), id, req));
    }

    @PutMapping("/cases/{id}/assign")
    @ModuleAccess(module = MODULE, action = Action.T)
    public ApiResponse<StaffCaseResponse> assign(@PathVariable UUID id, @RequestBody AssignRequest req) {
        return new ApiResponse<>(200, "ok.support.assigned", service.assign(actor(), id, req));
    }

    @PutMapping("/cases/{id}/status")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<StaffCaseResponse> status(@PathVariable UUID id, @RequestBody CaseStatusRequest req) {
        StaffCaseResponse r = service.changeStatus(actor(), id, req);
        String key = switch (r.status()) {
            case "RESOLVED" -> "ok.support.resolved";
            case "CLOSED" -> "ok.support.closed";
            default -> "ok.support.reopened";
        };
        return new ApiResponse<>(200, key, r);
    }

    @GetMapping("/cases/{id}/attachments/{attachmentId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ResponseEntity<byte[]> attachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return file(service.attachmentStaff(actor(), id, attachmentId));
    }

    @PostMapping("/maintenance/run")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<MaintenanceResult> maintenance() {
        AuthenticatedActor a = actor();
        if (a.role() != RoleType.SYSTEM_ADMIN) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        return new ApiResponse<>(200, "ok.support.maintenance", service.maintenance());
    }

    static ResponseEntity<byte[]> file(AttachmentContent c) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(c.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(c.contentType()))
                .body(c.data());
    }
}
