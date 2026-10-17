package features.auth.service;

import features.module.domain.AppModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import features.auth.dto.MenuItem;
import features.auth.dto.MenuResponse;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.Action;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.util.enums.RoleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menú dirigido por el servidor (spec 00 §6.5): solo módulos donde el actor tiene V efectivo. El front lo pinta tal cual
 * y jamás decide permisos; el back vuelve a verificar en cada endpoint.
 *
 * <p>Soporta un nivel de agrupación vía {@code module.parent_code} (M03): un módulo hijo visible se anida dentro de
 * {@link MenuItem#children()} de su padre en vez de aparecer suelto en la raíz. Un módulo con {@code route == null}
 * solo aparece en el menú si tiene al menos un hijo visible (encabezado colapsable, p. ej. GRP_ORG); si no tiene ruta
 * propia ni hijos visibles se omite igual que antes (p. ej. ORG_BRANDING, que es una pestaña, no un ítem de menú).
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final ModuleCatalog catalog;
    private final AuthorizationService authorization;
    private final ContextService contexts;

    public MenuResponse menu(AuthenticatedActor actor) {
        List<AppModule> published = catalog.published();

        // 1) Un MenuItem por módulo visible (actor tiene V), con su propia lista de hijos (vacía por ahora).
        Map<String, MenuItem> byCode = new LinkedHashMap<>();
        Map<String, List<MenuItem>> childrenOf = new LinkedHashMap<>();
        for (AppModule m : published) {
            Set<String> actions = authorization.effectiveActions(actor, m.getCode());
            if (!actions.contains(Action.V.name())) {
                continue;
            }
            List<String> ordered = Arrays.stream(Action.values()).map(Enum::name).filter(actions::contains).toList();
            List<MenuItem> kids = new ArrayList<>();
            childrenOf.put(m.getCode(), kids);
            byCode.put(m.getCode(), new MenuItem(m.getCode(), m.getNameEs(), m.getNameEn(), m.getRoute(), m.getIcon(), ordered, kids));
        }

        // 2) Anida cada módulo visible con parent_code bajo su padre (si el padre también es visible).
        Set<String> nested = new HashSet<>();
        for (AppModule m : published) {
            MenuItem item = byCode.get(m.getCode());
            if (item == null || m.getParentCode() == null) {
                continue;
            }
            List<MenuItem> parentKids = childrenOf.get(m.getParentCode());
            if (parentKids != null) {
                parentKids.add(item);
                nested.add(m.getCode());
            }
        }

        // 3) Raíz: módulos no anidados en otro, que tengan ruta propia o al menos un hijo visible.
        List<MenuItem> roots = new ArrayList<>();
        for (AppModule m : published) {
            if (nested.contains(m.getCode())) {
                continue;
            }
            MenuItem item = byCode.get(m.getCode());
            if (item == null) {
                continue;
            }
            if (item.route() != null || !item.children().isEmpty()) {
                roots.add(item);
            }
        }

        boolean limited = !actor.isStaff() && actor.role() == RoleType.ORG_ADMIN
                && !contexts.hasActiveContract(actor.organizationId());
        return new MenuResponse(roots, limited);
    }
}
