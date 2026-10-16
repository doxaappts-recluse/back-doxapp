package pe.dcs.app.features.catalog.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.dcs.app.features.catalog.dto.CatalogOption;
import pe.dcs.app.features.catalog.service.CatalogService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.PublicEndpoint;
import pe.dcs.app.util.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * M23 · Opciones para los selects de cualquier pantalla (solo lectura). Cualquier sesión de acceso las consulta:
 * un select nunca exige el módulo Catálogos (un usuario de sede o el portal también llenan formularios).
 */
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/catalogs")
@RequiredArgsConstructor
public class CatalogOptionsController {

    private final CatalogService service;
    private final AccessScopeResolver resolver;

    @GetMapping("/{type}/options")
    public ApiResponse<List<CatalogOption>> options(@PathVariable String type, @RequestParam(required = false) UUID parentId) {
        return new ApiResponse<>(200, "ok.common.sent", service.options(resolver.actor(), type, parentId));
    }
}
