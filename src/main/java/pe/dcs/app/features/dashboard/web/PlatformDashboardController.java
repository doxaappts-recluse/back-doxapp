package pe.dcs.app.features.dashboard.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.features.dashboard.dto.DashboardResponse;
import pe.dcs.app.features.dashboard.service.DashboardService;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

import java.time.LocalDate;

/** M01 · panel de plataforma (N1). SYSTEM_ADMIN y SYSTEM_SUPPORT: solo agregados de la plataforma (V1). */
@RestController
@RequestMapping("/api/v1/platform/dashboard")
@RequiredArgsConstructor
public class PlatformDashboardController {

    private static final String MODULE = "DASHBOARD";

    private final DashboardService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<DashboardResponse> get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(defaultValue = "false") boolean refresh) {
        AuthenticatedActor actor = guard.staffActor(resolver.actor());
        return new ApiResponse<>(200, "ok.common.sent", service.platform(actor, from, to, refresh));
    }
}
