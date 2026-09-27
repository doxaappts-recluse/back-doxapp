package pe.dcs.app.features.access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.access.dto.DelegableModule;
import pe.dcs.app.features.access.dto.PermissionItemDto;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.ModuleKind;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.authz.AuthorizationService;
import pe.dcs.app.security.authz.ContractGate;
import pe.dcs.app.security.authz.ModuleCatalog;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * [V12] no elevación + [V13] módulos no delegables. Un ORG_USER es del nivel N3: solo se le pueden dar módulos
 * publicados que declaren N3, marcados como delegables, contratados (si son contratables) y que quien delega ya posee
 * con esas mismas acciones. Lo comparten el asistente de delegación y los perfiles de permisos.
 */
@Component
@RequiredArgsConstructor
public class DelegationRules {

    private final ModuleCatalog catalog;
    private final ContractGate contractGate;
    private final AuthorizationService authorization;

    /** Acciones que {@code actor} posee sobre el módulo (vacío = no lo posee o no está contratado). */
    Set<String> possessed(AuthenticatedActor actor, AppModule m) {
        Set<String> eff = authorization.effectiveActions(actor, m.getCode());
        if (!eff.isEmpty()) {
            return eff;
        }
        // ORG_ADMIN es N2: un módulo declarado solo para N3 lo posee igualmente si está contratado (todo lo contratado).
        if (actor.role() == RoleType.ORG_ADMIN && m.isPublished() && m.levelList().contains("N3") && contracted(actor, m)) {
            return Set.copyOf(m.actionList());
        }
        return Set.of();
    }

    private boolean contracted(AuthenticatedActor actor, AppModule m) {
        return m.getKind() != ModuleKind.CONTRACTABLE || contractGate.enabled(actor.organizationId(), m.getCode());
    }

    /** Matriz que puede otorgar {@code actor} (módulos y acciones), en el orden del menú. */
    public List<DelegableModule> delegable(AuthenticatedActor actor) {
        List<DelegableModule> out = new ArrayList<>();
        for (AppModule m : catalog.published()) {
            if (!m.isDelegable() || !m.levelList().contains("N3") || "MY_ACCOUNT".equals(m.getCode())) {
                continue;
            }
            Set<String> have = possessed(actor, m);
            List<String> actions = m.actionList().stream().filter(have::contains).toList();
            if (!actions.isEmpty()) {
                out.add(new DelegableModule(m.getCode(), m.getNameEs(), m.getNameEn(), actions));
            }
        }
        return out;
    }

    /** Valida y normaliza una lista módulo × acciones. Devuelve módulo → acciones (con V primero); descarta renglones vacíos. */
    public Map<String, List<String>> normalize(AuthenticatedActor actor, List<PermissionItemDto> items) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        if (items != null) {
            for (PermissionItemDto it : items) {
                String code = it.moduleCode().trim().toUpperCase(Locale.ROOT);
                Set<String> acts = merged.computeIfAbsent(code, k -> new LinkedHashSet<>());
                if (it.actions() != null) {
                    it.actions().stream().filter(a -> a != null && !a.isBlank()).map(a -> a.trim().toUpperCase(Locale.ROOT)).forEach(acts::add);
                }
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : merged.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            AppModule m = catalog.find(e.getKey());
            if (m == null || !m.isPublished() || !m.levelList().contains("N3")) {
                throw new Exceptions("error.access.moduleInvalid", HttpStatus.UNPROCESSABLE_ENTITY, e.getKey());
            }
            if (!m.isDelegable() || "MY_ACCOUNT".equals(m.getCode())) {                       // [V13]
                throw new Exceptions("error.access.notDelegable", HttpStatus.UNPROCESSABLE_ENTITY, m.getCode());
            }
            Set<String> defined = Set.copyOf(m.actionList());
            if (!defined.containsAll(e.getValue())) {
                throw new Exceptions("error.access.moduleInvalid", HttpStatus.UNPROCESSABLE_ENTITY, m.getCode());
            }
            if (!possessed(actor, m).containsAll(e.getValue())) {                            // [V12]
                throw new Exceptions("error.access.escalation", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (!e.getValue().contains("V")) {
                throw new Exceptions("error.access.needView", HttpStatus.UNPROCESSABLE_ENTITY, m.getCode());
            }
            List<String> ordered = m.actionList().stream().filter(e.getValue()::contains).toList();
            out.put(m.getCode(), ordered);
        }
        return out;
    }
}
