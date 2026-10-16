package pe.dcs.app.features.event.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.event.dto.EventDtos;
import pe.dcs.app.features.event.service.EventPublicService;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

import java.util.UUID;

/** M14 · Vista e inscripción públicas de un evento publicado y marcado como público (sin sesión). Sin el módulo contratado: 404. */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/public/orgs/{slug}/events/{id}")
@RequiredArgsConstructor
public class PublicEventController {

    private final EventPublicService service;

    @GetMapping
    public ResponseEntity<EventDtos.PublicEventView> config(@PathVariable String slug, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(service.config(slug, id));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@PathVariable String slug, @PathVariable UUID id, @RequestBody EventDtos.PublicRegisterRequest req, HttpServletRequest http) {
        service.register(slug, id, http.getRemoteAddr(), req);
        return new ApiResponse<>(200, "ok.event.registered", null);
    }
}
