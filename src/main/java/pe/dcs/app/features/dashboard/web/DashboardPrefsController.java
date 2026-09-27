package pe.dcs.app.features.dashboard.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.dashboard.dto.DashboardPref;
import pe.dcs.app.features.dashboard.dto.DashboardPrefsRequest;
import pe.dcs.app.features.dashboard.service.DashboardService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

/** M01 · orden y widgets ocultos de cada persona. Es personal: cualquier sesión con panel administra lo suyo. */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/me/dashboard-prefs")
@RequiredArgsConstructor
public class DashboardPrefsController {

    private final DashboardService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    public ApiResponse<List<DashboardPref>> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.prefs(resolver.actor()));
    }

    @PutMapping
    public ApiResponse<List<DashboardPref>> save(@RequestBody DashboardPrefsRequest req) {
        return new ApiResponse<>(200, "ok.dashboard.prefsSaved", service.savePrefs(resolver.actor(), req == null ? null : req.prefs()));
    }
}
