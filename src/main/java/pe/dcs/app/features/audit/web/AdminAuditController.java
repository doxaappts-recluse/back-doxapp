package pe.dcs.app.features.audit.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.audit.dto.*;
import pe.dcs.app.features.audit.service.AuditQueryService;
import pe.dcs.app.features.audit.service.RetentionService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * M22 · auditoría de la organización (N2) y de la sede (N3). ORG_ADMIN ve toda la organización; ORG_BRANCH_ADMIN solo sus sedes
 * (otra sede → 404). Exportar y configurar la retención son solo del ORG_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private static final String MODULE = "AUDIT_LOG";
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AuditQueryService service;
    private final RetentionService retention;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;
    private final Clock clock;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @PostMapping("/audit/search")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<PageResponse<AuditEventResponse>> search(@RequestBody(required = false) AuditSearchRequest req) {
        return new ApiResponse<>(200, "ok.common.sent", service.searchOrg(actor(), req));
    }

    @GetMapping("/audit/filters")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AuditFilterOptions> filters() {
        return new ApiResponse<>(200, "ok.common.sent", service.filtersOrg(actor()));
    }

    @GetMapping("/audit/{id}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<AuditEventResponse> detail(@PathVariable long id) {
        return new ApiResponse<>(200, "ok.common.sent", service.detailOrg(actor(), id));
    }

    @PostMapping("/audit/export")
    @ModuleAccess(module = MODULE, action = Action.X)
    public ResponseEntity<byte[]> export(@RequestBody(required = false) AuditSearchRequest req) {
        AuditQueryService.Export ex = service.exportOrg(actor(), req);
        String name = "auditoria-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(clock.instant().atZone(ZoneOffset.UTC)) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Export-Rows", String.valueOf(ex.rows()))
                .contentType(XLSX)
                .body(ex.content());
    }

    @GetMapping("/retention")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<RetentionView> retention() {
        return new ApiResponse<>(200, "ok.common.sent", retention.viewOrg(actor()));
    }

    @PutMapping("/retention")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<RetentionView> setRetention(@RequestBody RetentionRequest req) {
        return new ApiResponse<>(200, "ok.retention.saved", retention.setOrg(actor(), req));
    }

    @DeleteMapping("/retention")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<RetentionView> clearRetention(@RequestParam(required = false) String moduleCode) {
        return new ApiResponse<>(200, "ok.retention.saved", retention.clearOrg(actor(), moduleCode));
    }
}
