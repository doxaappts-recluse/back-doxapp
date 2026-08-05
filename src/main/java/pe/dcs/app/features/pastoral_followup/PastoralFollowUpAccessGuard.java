package pe.dcs.app.features.pastoral_followup;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Seguimiento Pastoral (historial de contactos + peticiones
 * de oración, genéricos a cualquier Person). Junto a Visitantes (y
 * Asistencia a Cultos, ver ChurchAttendanceController que reutiliza
 * este mismo guard), forma parte del paquete comercial "CRM Pastoral"
 * — por eso SYSTEM NO tiene bypass acá (corregido: antes se seedeaba
 * como módulo base/gratuito, mismo criterio que Academia Bíblica en
 * adelante, ver DocumentTemplateAccessGuard). ORG_ADMIN/ORG_BRANCH_ADMIN
 * siempre; SÍ es delegable a ORG_USER igual que SMALL_GROUP/
 * FINANCIAL_MOVEMENT: con permiso CREATE puede registrar contactos/
 * peticiones de personas de su propia sede; con EDIT puede editar
 * cualquiera de su sede (no solo las que él mismo creó — el
 * seguimiento pastoral de una persona es responsabilidad de la sede,
 * no de quien tecleó el primer registro).
 *
 * Asignar/reasignar el líder responsable de una persona
 * (Person.assignedLeader) es una decisión de gestión: solo org
 * admin/branch admin, nunca delegable — ver assertCanAssignLeader().
 */
@Component
@RequiredArgsConstructor
public class PastoralFollowUpAccessGuard {

    private static final String MODULE_CODE = "PASTORAL_FOLLOWUP";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!permissions().isEmpty()) {
            return;
        }

        throw forbidden("action.accederSeguimientoPastoral");
    }

    public void assertCanCreate() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("action.registrarSeguimientoPastoral");
    }

    public void assertCanManage(Branch recordBranch) {

        if (!canManage(recordBranch)) {
            throw new Exceptions(
                    "error.noTienePermisosGestionarRegistroSeguimiento",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public boolean canManage(Branch recordBranch) {

        if (isAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchRecord =
                recordBranch != null
                        && currentBranchId != null
                        && recordBranch.getId().equals(currentBranchId);

        return ownBranchRecord && hasPermission("EDIT");
    }

    public void assertCanAssignLeader() {

        if (!isAdmin()) {
            throw new Exceptions(
                    "error.soloAdministradorOrganizacionSedePuedeAsignar",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Sede con la que se crea un contacto/petición: org admin elige
     * libremente; cualquier otro rol (branch admin, org user
     * delegado) siempre usa la sede de su contexto actual — mismo
     * criterio que MarriageServiceImpl/DocumentTemplateAccessGuard.
     */
    public UUID resolveBranchId(UUID requestedBranchId) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return requestedBranchId;
        }

        return authContext.getCurrentBranchId();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private boolean isAdmin() {
        return authContext.canManageOrgOrBranchOnly(
                authContext.getCurrentOrganizationId(),
                authContext.getCurrentBranchId()
        );
    }

    private boolean hasPermission(String code) {
        return permissions().contains(code);
    }

    private List<String> permissions() {

        UUID userId = authContext.getUserId();
        UUID organizationId = authContext.getCurrentOrganizationId();
        UUID branchId = authContext.getCurrentBranchId();

        if (userId == null || organizationId == null || branchId == null) {
            return List.of();
        }

        Module module =
                moduleRepository.findByCodeAndStatus(
                        MODULE_CODE,
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

    private Exceptions forbidden(String actionKey) {

        org.springframework.context.MessageSource messageSource = pe.dcs.app.util.MessageSourceHolder.get();

        String action = messageSource != null
                ? messageSource.getMessage(actionKey, null, actionKey, org.springframework.context.i18n.LocaleContextHolder.getLocale())
                : actionKey;

        return new Exceptions("error.noTienePermisosPara", HttpStatus.FORBIDDEN, action);
    }
}
