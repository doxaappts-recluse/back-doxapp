package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.auth.dto.MenuItem;
import pe.dcs.app.features.auth.dto.MenuResponse;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.util.enums.RoleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Menú dirigido por el servidor (spec 00 §6.5): solo módulos donde el actor tiene V efectivo. El front lo pinta tal cual
 * y jamás decide permisos; el back vuelve a verificar en cada endpoint.
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final ModuleCatalog catalog;
    private final AuthorizationService authorization;
    private final ContextService contexts;

    public MenuResponse menu(AuthenticatedActor actor) {
        List<MenuItem> items = new ArrayList<>();
        for (AppModule m : catalog.published()) {
            if (m.getRoute() == null) {
                continue;
            }
            Set<String> actions = authorization.effectiveActions(actor, m.getCode());
            if (!actions.contains(Action.V.name())) {
                continue;
            }
            List<String> ordered = Arrays.stream(Action.values()).map(Enum::name).filter(actions::contains).toList();
            items.add(new MenuItem(m.getCode(), m.getNameEs(), m.getNameEn(), m.getRoute(), m.getIcon(), ordered));
        }
        boolean limited = !actor.isStaff() && actor.role() == RoleType.ORG_ADMIN
                && !contexts.hasActiveContract(actor.organizationId());
        return new MenuResponse(items, limited);
    }
}
