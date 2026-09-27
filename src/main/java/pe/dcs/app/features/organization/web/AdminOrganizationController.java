package pe.dcs.app.features.organization.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.dcs.app.features.organization.dto.MyOrganizationResponse;
import pe.dcs.app.features.organization.dto.OrgSelfUpdateRequest;
import pe.dcs.app.features.organization.service.OrganizationService;
import pe.dcs.app.security.authz.AccessScopeResolver;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.ModuleAccess;
import pe.dcs.app.util.ApiResponse;

/**
 * M02 · "Mi organización" (N2/N3). ORG_ADMIN consulta y edita contacto/dirección/idioma/año fiscal; ORG_BRANCH_ADMIN
 * solo consulta (el servicio rechaza su edición con 403, aunque el módulo declare la acción E para N3).
 */
@RestController
@RequestMapping("/api/v1/admin/organization")
@RequiredArgsConstructor
public class AdminOrganizationController {

    private static final String MODULE = "ORGANIZATION";

    private final OrganizationService service;
    private final AccessScopeResolver resolver;

    @GetMapping
    @ModuleAccess(module = MODULE, action = Action.V)
    public ApiResponse<MyOrganizationResponse> get() {
        return new ApiResponse<>(200, "ok.common.sent", service.mine(resolver.actor()));
    }

    @PutMapping
    @ModuleAccess(module = MODULE, action = Action.E)
    public ApiResponse<MyOrganizationResponse> update(@Valid @RequestBody OrgSelfUpdateRequest req) {
        return new ApiResponse<>(200, "ok.org.updated", service.updateMine(req, resolver.actor()));
    }
}
