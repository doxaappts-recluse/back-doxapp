package pe.dcs.app.features.support.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import pe.dcs.app.util.pagination.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * M22 · casos de soporte de la organización (N2/N3). Ver (V) y abrir o responder (C): ORG_ADMIN todos los casos, ORG_BRANCH_ADMIN los
 * de sus sedes y los suyos, ORG_USER solo los suyos. Nunca se envían las notas internas de plataforma.
 */
@RestController
@RequestMapping("/api/v1/admin/support")
@RequiredArgsConstructor
public class AdminSupportController {

    private static final String MODULE = "SUPPORT";

    private final SupportService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @PostMapping("/cases/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<OrgCaseResponse>> search(@RequestBody(required = false) CaseSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.searchOrg(actor(), req));
    }

    @GetMapping("/cases/summary")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<OrgSummary> summary() {
        return new ApiResponse<>(200, "ok.common.sent", service.summaryOrg(actor()));
    }

    @PostMapping(value = "/cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<OrgCaseResponse> open(@RequestParam("category") String category,
                                             @RequestParam(value = "priority", required = false) String priority,
                                             @RequestParam("subject") String subject,
                                             @RequestParam(value = "body", required = false) String body,
                                             @RequestParam(value = "branchId", required = false) UUID branchId,
                                             @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                             @RequestParam(value = "requestedType", required = false) String requestedType,
                                             @RequestParam(value = "branchName", required = false) String branchName,
                                             @RequestParam(value = "branchCode", required = false) String branchCode,
                                             @RequestParam(value = "branchDisplayName", required = false) String branchDisplayName,
                                             @RequestParam(value = "branchPhone", required = false) String branchPhone,
                                             @RequestParam(value = "branchEmail", required = false) String branchEmail,
                                             @RequestParam(value = "branchOpeningDate", required = false) String branchOpeningDate,
                                             @RequestParam(value = "branchTimezone", required = false) String branchTimezone,
                                             @RequestParam(value = "branchAddressLine", required = false) String branchAddressLine,
                                             @RequestParam(value = "branchAddressCity", required = false) String branchAddressCity,
                                             @RequestParam(value = "branchAddressCountry", required = false) String branchAddressCountry) {
        // M22-T11/T12 · solo relevantes para category=CONTRACT_CHANGE (requestedType) o NEW_BRANCH (branch*); ver SupportService.open().
        NewBranchDraft draft = branchName == null && branchCode == null ? null
                : new NewBranchDraft(branchName, branchCode, branchDisplayName, branchPhone, branchEmail, branchOpeningDate,
                        branchTimezone, branchAddressLine, branchAddressCity, branchAddressCountry);
        return new ApiResponse<>(201, "ok.support.opened",
                service.open(actor(), category, priority, subject, body, branchId, files, requestedType, draft));
    }

    @GetMapping("/cases/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<OrgCaseResponse> detail(@PathVariable UUID id) {
        return new ApiResponse<>(200, "ok.common.sent", service.detailOrg(actor(), id));
    }

    @PostMapping(value = "/cases/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<OrgCaseResponse> reply(@PathVariable UUID id,
                                              @RequestParam(value = "body", required = false) String body,
                                              @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        return new ApiResponse<>(200, "ok.support.replied", service.replyOrg(actor(), id, body, files));
    }

    @PostMapping("/cases/{id}/rate")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<OrgCaseResponse> rate(@PathVariable UUID id, @RequestBody RateRequest req) {
        return new ApiResponse<>(200, "ok.support.rated", service.rate(actor(), id, req));
    }

    @GetMapping("/cases/{id}/attachments/{attachmentId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ResponseEntity<byte[]> attachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        return PlatformSupportController.file(service.attachmentOrg(actor(), id, attachmentId));
    }
}
