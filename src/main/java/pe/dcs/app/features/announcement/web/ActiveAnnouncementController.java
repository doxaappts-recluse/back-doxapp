package pe.dcs.app.features.announcement.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.announcement.dto.ActiveAnnouncement;
import pe.dcs.app.features.announcement.service.AnnouncementService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

import java.util.List;

/** M22 · anuncios vigentes para cualquier sesión de organización o de portal (el banner no exige ningún módulo). */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class ActiveAnnouncementController {

    private final AnnouncementService service;
    private final AccessScopeResolver resolver;

    @GetMapping("/active")
    public ApiResponse<List<ActiveAnnouncement>> active() {
        return new ApiResponse<>(200, "ok.common.sent", service.active(resolver.actor()));
    }
}
