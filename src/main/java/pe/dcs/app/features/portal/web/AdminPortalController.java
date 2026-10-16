package pe.dcs.app.features.portal.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.portal.dto.PortalDtos.AccessRow;
import pe.dcs.app.features.portal.dto.PortalDtos.DisableRequest;
import pe.dcs.app.features.portal.dto.PortalDtos.InviteRequest;
import pe.dcs.app.features.portal.dto.PortalDtos.SettingsRequest;
import pe.dcs.app.features.portal.dto.PortalDtos.SettingsResponse;
import pe.dcs.app.features.portal.service.PortalAccessAdminService;
import pe.dcs.app.features.portal.service.PortalSettingsService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M24 N2/N3 · administración del portal: ajustes (organización y override de sede), invitaciones y estado del
 * acceso. Las decisiones de autorregistro (PORTAL_SIGNUP) NO tienen endpoint propio: se deciden desde la bandeja
 * genérica ya existente, {@code /admin/approvals} (M21), filtrando por {@code type=PORTAL_SIGNUP}.
 */
@RestController
@RequestMapping("/api/v1/admin/portal")
@RequiredArgsConstructor
public class AdminPortalController {

    private static final String MODULE = "PORTAL";

    private final PortalSettingsService settings;
    private final PortalAccessAdminService access;
    private final AccessScopeResolver resolver;

    @GetMapping("/settings")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<SettingsResponse> getSettings() {
        return new ApiResponse<>(200, "ok.common.sent", settings.getOrg(resolver.current().organizationId()));
    }

    @PostMapping("/settings")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<SettingsResponse> updateSettings(@RequestBody SettingsRequest req) {
        return new ApiResponse<>(200, "ok.portal.settingsSaved", settings.updateOrg(resolver.actor(), resolver.current(), req));
    }

    @GetMapping("/settings/branch/{branchId}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<SettingsResponse> getBranchSettings(@PathVariable UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", settings.getBranch(resolver.current().organizationId(), branchId));
    }

    @PostMapping("/settings/branch/{branchId}")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<SettingsResponse> updateBranchSettings(@PathVariable UUID branchId, @RequestBody SettingsRequest req) {
        return new ApiResponse<>(200, "ok.portal.settingsSaved", settings.updateBranch(resolver.actor(), resolver.current(), branchId, req));
    }

    @GetMapping("/access")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<AccessRow>> listAccess(@RequestParam(required = false) UUID branchId) {
        return new ApiResponse<>(200, "ok.common.sent", access.list(resolver.current(), branchId));
    }

    @PostMapping("/invitations")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AccessRow> invite(@RequestBody InviteRequest req) {
        return new ApiResponse<>(200, "msg.portal.invitationSent", access.invite(resolver.actor(), resolver.current(), req));
    }

    @PostMapping("/access/{personId}/resend")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<Void> resend(@PathVariable UUID personId) {
        access.resend(resolver.actor(), resolver.current(), personId);
        return new ApiResponse<>(200, "msg.portal.invitationSent", null);
    }

    @PostMapping("/access/{personId}/disable")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AccessRow> disable(@PathVariable UUID personId, @RequestBody(required = false) DisableRequest req) {
        return new ApiResponse<>(200, "ok.portal.accessDisabled",
                access.setEnabled(resolver.actor(), resolver.current(), personId, false, req == null ? null : req.reason()));
    }

    @PostMapping("/access/{personId}/enable")
    @ModuleAccess(module = MODULE, action = Action.C)
    public ApiResponse<AccessRow> enable(@PathVariable UUID personId) {
        return new ApiResponse<>(200, "ok.portal.accessEnabled", access.setEnabled(resolver.actor(), resolver.current(), personId, true, null));
    }
}
