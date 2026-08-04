package pe.dcs.app.features.visibility;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.DataAccessRule;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.repository.DataAccessRuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.PersonBranchRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.repository.VisibilityGrantRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.rules.DataScope;

import java.util.List;
import java.util.UUID;

/**
 * Guard compartido de visibilidad entre sedes para módulos con
 * historial "dueño de una sede" (Membresía, Servicio Ministerial,
 * Bautizo por ahora — ver DataAccessRule). Se usa en el momento
 * de armar la respuesta (no en el Specification), porque la
 * decisión depende del registro puntual (su sede dueña), no del
 * filtro de búsqueda.
 *
 * Reglas, en orden:
 * 1) Registro sin sede dueña (branch null, dato previo a esta
 *    columna) -> siempre visible.
 * 2) SYSTEM -> siempre visible.
 * 3) Admin de la organización dueña del registro -> siempre
 *    visible.
 * 4) La sede actual del que consulta ES la sede dueña -> visible.
 * 5) Si no hay DataAccessRule habilitada para el módulo -> visible
 *    (sin restricción configurada, no rompe nada existente).
 * 6) Según el scope de la regla: ORGANIZATION siempre visible
 *    (ya se filtró por organización en el Specification);
 *    CURRENT_BRANCH nunca visible fuera de su sede;
 *    PERSON_HISTORY visible si la persona estuvo alguna vez en la
 *    sede que consulta; APPROVAL_REQUIRED visible solo si existe
 *    un VisibilityGrant activo de la sede dueña hacia la sede que
 *    consulta.
 */
@Component
@RequiredArgsConstructor
public class VisibilityGuard {

    private final AuthContext authContext;
    private final DataAccessRuleRepository dataAccessRuleRepository;
    private final VisibilityGrantRepository visibilityGrantRepository;
    private final PersonBranchRepository personBranchRepository;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    public boolean canView(Branch recordBranch, UUID personId, String moduleCode) {

        if (recordBranch == null) {
            return true;
        }

        if (authContext.isSystem()) {
            return true;
        }

        UUID organizationId =
                recordBranch.getOrganization().getId();

        if (authContext.isOrganizationAdmin(organizationId)) {
            return true;
        }

        UUID currentBranchId =
                authContext.getCurrentBranchId();

        if (currentBranchId != null
                && currentBranchId.equals(recordBranch.getId())) {
            return true;
        }

        DataAccessRule rule =
                dataAccessRuleRepository
                        .findByModule_CodeAndEnabledTrue(moduleCode)
                        .orElse(null);

        if (rule == null) {
            return true;
        }

        DataScope scope = rule.getScope();

        if (scope == DataScope.ORGANIZATION) {
            return true;
        }

        if (scope == DataScope.CURRENT_BRANCH) {
            return false;
        }

        if (scope == DataScope.PERSON_HISTORY) {
            return personHasHistoryAt(personId, currentBranchId);
        }

        // APPROVAL_REQUIRED
        return currentBranchId != null
                && visibilityGrantRepository.findActive(
                        personId,
                        recordBranch.getId(),
                        currentBranchId,
                        moduleCode
                ).isPresent();
    }

    /**
     * ¿Puede aprobar/rechazar solicitudes de visibilidad sobre
     * data de la sede "sourceBranchId"? SYSTEM y el admin de la
     * organización dueña siempre pueden; un admin de la propia
     * sede dueña también; un org user delegado con permiso EDIT
     * en el módulo, siempre que esté operando en el contexto de
     * esa misma sede dueña.
     */
    public boolean canApprove(Branch sourceBranch, String moduleCode) {

        if (authContext.isSystem()) {
            return true;
        }

        UUID organizationId =
                sourceBranch.getOrganization().getId();

        if (authContext.isOrganizationAdmin(organizationId)) {
            return true;
        }

        UUID currentBranchId =
                authContext.getCurrentBranchId();

        if (currentBranchId == null
                || !currentBranchId.equals(sourceBranch.getId())) {
            return false;
        }

        if (authContext.isCurrentBranchAdmin()) {
            return true;
        }

        return modulePermissions(moduleCode).contains("EDIT");
    }

    private boolean personHasHistoryAt(UUID personId, UUID branchId) {

        if (personId == null || branchId == null) {
            return false;
        }

        return personBranchRepository
                .findByPersonIdOrderByStartDateDesc(personId)
                .stream()
                .anyMatch(pb ->
                        pb.getBranch() != null
                                && branchId.equals(pb.getBranch().getId())
                );
    }

    private List<String> modulePermissions(String moduleCode) {

        UUID userId = authContext.getUserId();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        if (userId == null || organizationId == null || branchId == null) {
            return List.of();
        }

        Module module =
                moduleRepository.findByCodeAndStatus(
                        moduleCode,
                        StatusType.ACTIVE
                ).orElse(null);

        if (module == null) {
            return List.of();
        }

        return userAccessModulePermissionRepository.findPermissionsByAccessContext(
                userId,
                organizationId,
                branchId,
                module.getId(),
                StatusType.ACTIVE
        );
    }
}
