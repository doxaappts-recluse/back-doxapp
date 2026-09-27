package pe.dcs.app.security.authz;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.access.domain.UserAccessPermission;
import pe.dcs.app.features.access.repo.UserAccessPermissionRepository;
import pe.dcs.app.features.module.domain.AppModule;
import pe.dcs.app.features.module.domain.ModuleKind;
import pe.dcs.app.features.support.domain.AssistedAccessGrant;
import pe.dcs.app.features.support.domain.AssistedAccessGrantRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.TokenType;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spec 00 §6.4 · permiso efectivo = rol ∧ nivel ∧ contrato ∧ delegación ∧ acceso vigente.
 * <ul>
 *   <li>Nivel: el módulo debe declarar el nivel del rol (N1 plataforma … N4 portal). Así el personal de plataforma
 *       queda fuera de todo módulo operativo sin necesidad de reglas especiales (D3: sin bypass).</li>
 *   <li>Contrato [K]: módulos CONTRACTABLE solo si están en el contrato ACTIVE. Sin contrato ACTIVE, ORG_ADMIN entra en
 *       modo limitado (solo lectura de {@link #LIMITED_MODE}); los demás roles no operan.</li>
 *   <li>ORG_ADMIN = todo lo contratado; ORG_BRANCH_ADMIN = todo lo contratado en sus sedes; ORG_USER = solo lo delegado.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    /** Módulos disponibles para ORG_ADMIN sin contrato ACTIVE (solo lectura, más solicitar renovación en soporte). */
    static final Set<String> LIMITED_MODE = Set.of("ORGANIZATION", "ORG_BRANDING", "ORG_CONTRACT_VIEW", "SUPPORT", "MY_ACCOUNT");

    /**
     * M22 (D3, acceso asistido): únicos módulos de "configuración" que un grant puede incluir — más conservador que
     * el spec (que también lista M05: equipo/accesos de la organización), a propósito, ver la cabecera de
     * V37__assisted_access_m22.sql. {@code AssistedAccessService} usa esta misma lista para rechazar con
     * {@code error.assisted.scopeNotAllowed} [V4] un alcance que pida un módulo fuera de ella.
     */
    public static final Set<String> ASSISTED_SCOPE_MODULES = Set.of(
            "ORGANIZATION", "ORG_BRANDING", "BRANCH", "CATALOGS", "ORG_SETTINGS", "INTEGRATIONS", "DOC_TEMPLATES");

    private final ModuleCatalog catalog;
    private final ContractGate contractGate;
    private final AccessStateCache accessState;
    private final UserAccessPermissionRepository delegations;
    private final AssistedAccessGrantRepository assistedGrants;
    private final Clock clock;

    public Set<String> effectiveActions(AuthenticatedActor actor, String moduleCode) {
        if (actor.tokenType() != TokenType.ACCESS) {
            return Set.of();
        }
        AppModule module = catalog.find(moduleCode);
        if (module == null || !module.isPublished()) {
            return Set.of();
        }
        if (actor.isStaff() && actor.isAssisted()) {
            return assistedStaffActions(actor, module, moduleCode);
        }
        if (!module.levelList().contains(actor.role().levelCode())) {
            return Set.of();
        }
        Set<String> moduleActions = Set.copyOf(module.actionList());

        if (!accessState.isEffective(actor)) {
            return Set.of();
        }
        if (actor.isStaff()) {
            return staffActions(actor.role(), moduleCode, moduleActions);
        }

        boolean hasContract = contractGate.hasActiveContract(actor.organizationId());
        if (!hasContract) {
            return limitedMode(actor, module, moduleActions);
        }
        if (module.getKind() == ModuleKind.CONTRACTABLE && !contractGate.enabled(actor.organizationId(), moduleCode)) {
            return Set.of();
        }

        return switch (actor.role()) {
            case ORG_ADMIN -> moduleActions;
            case ORG_BRANCH_ADMIN -> branchAdminActions(moduleCode, moduleActions);
            case ORG_USER -> delegatedActions(actor, module, moduleActions);
            case MEMBER -> memberActions(module, moduleActions);
            default -> Set.of();
        };
    }

    /** Lanza 403 si el actor no puede ejecutar la acción; distingue "módulo no contratado" de "sin permiso". */
    public void require(AuthenticatedActor actor, String moduleCode, Action action) {
        if (effectiveActions(actor, moduleCode).contains(action.name())) {
            return;
        }
        if (isNotContracted(actor, moduleCode)) {
            throw new Exceptions("error.common.moduleNotContracted", HttpStatus.FORBIDDEN);
        }
        throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
    }

    private boolean isNotContracted(AuthenticatedActor actor, String moduleCode) {
        if (actor.isStaff() || actor.organizationId() == null) {
            return false;
        }
        AppModule m = catalog.find(moduleCode);
        return m != null && m.getKind() == ModuleKind.CONTRACTABLE
                && m.levelList().contains(actor.role().levelCode())
                && !contractGate.enabled(actor.organizationId(), moduleCode);
    }

    /** Módulos donde SYSTEM_SUPPORT hace más que consultar (M22: responde, asigna y cierra casos). */
    private static final Map<String, Set<String>> SUPPORT_STAFF_EXTRA = Map.of("SUPPORT", Set.of("V", "C", "E", "S", "T"));

    /**
     * Tope de acciones del administrador de sede en módulos con acciones sensibles: en PERSON no recibe H (datos reservados), M (fusionar),
     * I (importar) ni P (anonimizar) mientras la organización no tenga forma de concedérselas (spec M06 N3).
     */
    private static final Map<String, Set<String>> BRANCH_ADMIN_CAPS = Map.ofEntries(
            Map.entry("PERSON", Set.of("V", "C", "E", "S", "X")), Map.entry("VISITOR", Set.of("V", "C", "E", "S", "X")),
            Map.entry("BRANCH_TRANSFER", Set.of("V", "C", "A")), Map.entry("VISIBILITY_RULES", Set.of("V", "C", "A")),
            Map.entry("ATTENDANCE", Set.of("V", "C", "E", "S", "A", "X", "T")),
            Map.entry("MEMBERSHIP", Set.of("V", "C", "E", "S", "A", "X")), Map.entry("BAPTISM", Set.of("V", "C", "E", "S", "A", "X")),
            Map.entry("MARRIAGE", Set.of("V", "C", "E", "S", "A", "X")), Map.entry("CHILD_DEDICATION", Set.of("V", "C", "E", "S", "A", "X")),
            Map.entry("SMALL_GROUP", Set.of("V", "C", "E", "S", "A", "X")),
            Map.entry("MINISTRY", Set.of("V", "C", "E", "S", "A", "X", "Q")),
            Map.entry("VOLUNTEER_SCHEDULING", Set.of("V", "C", "E", "S", "P", "X")),
            Map.entry("PASTORAL_CARE", Set.of("V", "C", "E", "S", "A", "X")),
            Map.entry("TRAINING", Set.of("V", "C", "E", "S", "O", "X")),
            Map.entry("EVENTS", Set.of("V", "C", "E", "S", "P", "T", "X", "O")),
            Map.entry("SPACES", Set.of("V", "C", "E", "S", "A", "X")),
            Map.entry("INVENTORY", Set.of("V", "C", "E", "D", "S", "X", "I")),
            // [D1 de M17] "por defecto NO delegado": el administrador de sede no recibe nada de RRHH por su rol; si N2 delega,
            // lo hace por el camino de ORG_USER (los tres módulos son delegable=true) — ver cabecera de V28__hr_payroll_m17.sql.
            Map.entry("HR_STAFF", Set.of()), Map.entry("HR_LEAVE", Set.of()), Map.entry("HR_PAYROLL", Set.of()),
            // M18: sin dato sensible que retener (a diferencia de PERSON/H) — el administrador de sede recibe el módulo completo.
            Map.entry("DOC_TEMPLATES", Set.of("V", "C", "E", "D", "S", "I", "Y", "X")),
            // M20: spec N3 "ORG_BRANCH_ADMIN V X (su sede)" — igual que N2 en ver/exportar; la programación de envíos
            // (sin acción propia, ver AdminScheduledReportController) queda reservada a ORG_ADMIN de todos modos.
            Map.entry("REPORTS", Set.of("V", "X")),
            // M24: spec N3 "invita miembros de su sede, decide autorregistros, ve/reenvía/revoca acceso" — sin E:
            // el modo de autorregistro, homeBlocks y textos de bienvenida globales los decide solo ORG_ADMIN (N2).
            Map.entry("PORTAL", Set.of("V", "C")),
            // M22 (D3, acceso asistido): SUPPORT ganó la acción 'A' (aprobar) en V37 para que ORG_ADMIN pueda
            // aprobar/rechazar/revocar (recibe todas las acciones declaradas del módulo, sin pasar por este mapa).
            // Sin este tope, ORG_BRANCH_ADMIN recibiría 'A' también por la misma vía, violando [V9] del spec
            // ("solo ORG_ADMIN aprueba acceso asistido de su org") — se congela aquí exactamente lo que ya tenía
            // antes de V37 (V,C,E,S,T), sin agregar ni quitar nada más de lo que ya venía funcionando.
            Map.entry("SUPPORT", Set.of("V", "C", "E", "S", "T")));

    /**
     * M22 (D3, acceso asistido): reemplaza el chequeo normal de nivel para personal de plataforma con un grant
     * activo — el único bypass de nivel que existe en todo el sistema, y solo para los módulos de {@link
     * #ASSISTED_SCOPE_MODULES}. El grant se vuelve a leer de la base en cada llamada (no se confía en que el JWT siga
     * vigente): así una revocación del ORG_ADMIN corta el acceso al instante, sin esperar a que expire el token.
     */
    private Set<String> assistedStaffActions(AuthenticatedActor actor, AppModule module, String moduleCode) {
        if (!ASSISTED_SCOPE_MODULES.contains(moduleCode)) {
            return Set.of();
        }
        AssistedAccessGrant grant = assistedGrants.findById(actor.assistedGrantId()).orElse(null);
        if (grant == null || !grant.getStaffId().equals(actor.ownerId())
                || !grant.getOrganizationId().equals(actor.organizationId())
                || !grant.isLiveActive(clock.instant())
                || !java.util.List.of(grant.getScope()).contains(moduleCode)) {
            return Set.of();
        }
        if (module.getKind() == ModuleKind.CONTRACTABLE && !contractGate.enabled(actor.organizationId(), moduleCode)) {
            return Set.of();
        }
        return Set.copyOf(module.actionList());
    }

    private Set<String> branchAdminActions(String moduleCode, Set<String> moduleActions) {
        Set<String> cap = BRANCH_ADMIN_CAPS.get(moduleCode);
        if (cap == null) {
            return moduleActions;
        }
        return moduleActions.stream().filter(cap::contains).collect(java.util.stream.Collectors.toSet());
    }

    private Set<String> staffActions(RoleType role, String moduleCode, Set<String> moduleActions) {
        // SYSTEM_ADMIN: todas las acciones de los módulos N1. SYSTEM_SUPPORT: solo lectura salvo lo que M22 declare aparte.
        if (role == RoleType.SYSTEM_ADMIN) {
            return moduleActions;
        }
        Set<String> allowed = SUPPORT_STAFF_EXTRA.getOrDefault(moduleCode, Set.of(Action.V.name()));
        return allowed.stream().filter(moduleActions::contains).collect(java.util.stream.Collectors.toSet());
    }

    private Set<String> limitedMode(AuthenticatedActor actor, AppModule module, Set<String> moduleActions) {
        if (actor.role() != RoleType.ORG_ADMIN || !LIMITED_MODE.contains(module.getCode())) {
            return Set.of();
        }
        Set<String> allowed = new HashSet<>();
        allowed.add(Action.V.name());
        if ("SUPPORT".equals(module.getCode()) || "MY_ACCOUNT".equals(module.getCode())) {
            allowed.addAll(Set.of(Action.C.name(), Action.E.name()));
        }
        allowed.retainAll(moduleActions);
        return allowed;
    }

    private Set<String> delegatedActions(AuthenticatedActor actor, AppModule module, Set<String> moduleActions) {
        if (module.getCode().equals("MY_ACCOUNT")) {
            return moduleActions;
        }
        if (!module.isDelegable()) {
            return Set.of();
        }
        Set<String> granted = delegations.findByAccessIdAndModuleCode(actor.contextId(), module.getCode())
                .map(UserAccessPermission::actionList).map(Set::copyOf).orElse(Set.of());
        Set<String> result = new HashSet<>(granted);
        result.retainAll(moduleActions);
        return result;
    }

    /**
     * M24 (portal del miembro): el módulo base PORTAL (alta, home, ajustes) no pasa por esta puerta — sus rutas
     * {@code /portal/me/**} las guarda directo {@code PortalGuard.requireMember} (403 error.portal.wrongArea para
     * cualquier otro rol, incluido ORG_ADMIN, que ya tiene V/C/E sobre PORTAL para SU propia vista de administración).
     * Aquí solo se declara PORTAL_DIRECTORY, que sí necesita el candado de contrato [K] del módulo CONTRACTABLE
     * opcional. Las secciones de otros módulos (perfil, solicitudes...) tampoco pasan por aquí: leen directo del
     * módulo dueño, acotadas siempre a la propia persona del token (nunca a un id de la URL — anti-IDOR [V13]).
     */
    private static final Map<String, Set<String>> MEMBER_CAPS = Map.of("PORTAL_DIRECTORY", Set.of("V"));

    private Set<String> memberActions(AppModule module, Set<String> moduleActions) {
        if (module.getCode().equals("MY_ACCOUNT")) {
            return moduleActions;
        }
        Set<String> cap = MEMBER_CAPS.get(module.getCode());
        if (cap == null) {
            return Set.of();
        }
        return moduleActions.stream().filter(cap::contains).collect(java.util.stream.Collectors.toSet());
    }
}
