package pe.dcs.app.features.integration.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.integration.domain.IntegrationProvider;
import pe.dcs.app.features.integration.dto.IntegrationDtos;
import pe.dcs.app.features.integration.service.IntegrationService;
import pe.dcs.app.features.config.service.OrgGuard;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;
import pe.dcs.app.util.Exceptions;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * M23 · Integraciones (N2, módulo contratable). Solo ORG_ADMIN. Nunca devuelve credenciales: solo {@code secretsConfigured}.
 * No hay endpoints de plataforma: SYSTEM_ADMIN tampoco ve los secretos de una organización.
 */
@RestController
@RequestMapping("/api/v1/admin/integrations")
@RequiredArgsConstructor
public class AdminIntegrationController {

    private static final String MODULE = "INTEGRATIONS";

    private final IntegrationService service;
    private final AccessScopeResolver resolver;
    private final OrgGuard guard;

    private AuthenticatedActor actor() {
        return guard.orgActor(resolver.actor());
    }

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<List<IntegrationDtos.Card>> list() {
        return new ApiResponse<>(200, "ok.common.sent", service.list(actor()));
    }

    @GetMapping("/{provider}")
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<IntegrationDtos.Card> get(@PathVariable String provider) {
        return new ApiResponse<>(200, "ok.common.sent", service.get(actor(), parse(provider)));
    }

    @PutMapping("/{provider}")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<IntegrationDtos.Card> save(@PathVariable String provider, @RequestBody IntegrationDtos.SaveRequest req) {
        return new ApiResponse<>(200, "ok.integration.saved", service.save(actor(), parse(provider), req.settings(), req.secrets()));
    }

    @PostMapping("/{provider}/test")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<IntegrationDtos.Card> test(@PathVariable String provider) {
        return new ApiResponse<>(200, "ok.integration.tested", service.test(actor(), parse(provider)));
    }

    @PostMapping("/{provider}/rotate")
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<IntegrationDtos.Card> rotate(@PathVariable String provider, @RequestBody IntegrationDtos.RotateRequest req) {
        return new ApiResponse<>(200, "ok.integration.rotated", service.rotate(actor(), parse(provider), req.secrets()));
    }

    @PostMapping("/{provider}/activate")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<IntegrationDtos.Card> activate(@PathVariable String provider) {
        return new ApiResponse<>(200, "ok.integration.activated", service.activate(actor(), parse(provider)));
    }

    @PostMapping("/{provider}/disable")
    @ModuleAccess(module = MODULE, action = Action.S)
    public ApiResponse<IntegrationDtos.Card> disable(@PathVariable String provider) {
        return new ApiResponse<>(200, "ok.integration.disabled", service.disable(actor(), parse(provider)));
    }

    private static IntegrationProvider parse(String p) {
        try {
            return IntegrationProvider.valueOf(p == null ? "" : p.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
    }
}
