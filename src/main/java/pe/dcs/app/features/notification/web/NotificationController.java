package pe.dcs.app.features.notification.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.notification.dto.NotificationView;
import pe.dcs.app.features.notification.dto.UnreadCount;
import pe.dcs.app.features.notification.service.NotificationService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.pagination.PageResponse;

import java.util.UUID;

/** M19 base · bandeja propia (campana). Cualquier sesión ve solo lo suyo; no requiere permiso de módulo. */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    public ApiResponse<PageResponse<NotificationView>> list(@RequestParam(defaultValue = "false") boolean unread,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return new ApiResponse<>(200, "ok.common.sent", service.list(resolver.actor(), unread, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> unread() {
        return new ApiResponse<>(200, "ok.common.sent", new UnreadCount(service.unread(resolver.actor())));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable UUID id) {
        service.markRead(resolver.actor(), id);
        return new ApiResponse<>(200, "ok.notification.read", null);
    }

    @PutMapping("/read-all")
    public ApiResponse<UnreadCount> readAll() {
        service.markAllRead(resolver.actor());
        return new ApiResponse<>(200, "ok.notification.read", new UnreadCount(0));
    }
}
