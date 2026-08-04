package pe.dcs.app.features.document_template;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.DocumentTemplate;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Plantillas de Documentos. A diferencia del resto de
 * módulos, SYSTEM queda completamente fuera — no es su rol
 * interferir en la lógica de negocio de una organización/sede (ver
 * import.sql, comentario de DOCUMENT_TEMPLATES). Mismo mecanismo de
 * delegación que {@link pe.dcs.app.features.event.impl.EventAccessGuard}
 * / {@link pe.dcs.app.features.smallgroup.SmallGroupAccessGuard}:
 *
 * - Org admin: gestiona TODA plantilla de su organización — es el
 *   único que elige libremente si una plantilla es org-wide o de
 *   una sede puntual (ver resolveBranchId).
 * - Branch admin: gestiona solo la plantilla de SU propia sede — la
 *   sede sale siempre de su contexto, nunca la elige ni puede
 *   dejarla org-wide.
 * - Org user delegado (módulo DOCUMENT_TEMPLATES asignado vía
 *   Usuarios de Acceso, con permiso CREATE/EDIT): mismo alcance que
 *   un branch admin, acotado a su propia sede.
 */
@Component
@RequiredArgsConstructor
public class DocumentTemplateAccessGuard {

    private static final String MODULE_CODE = "DOCUMENT_TEMPLATES";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    public void assertCanCreate() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("crear plantillas de documentos");
    }

    /** ¿Puede usar el módulo (listar)? Admin siempre; org user delegado con cualquier permiso. */
    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!permissions().isEmpty()) {
            return;
        }

        throw forbidden("acceder a plantillas de documentos");
    }

    public void assertCanManage(DocumentTemplate template) {

        if (!canManage(template)) {
            throw new Exceptions(
                    "Solo el administrador de la organización, el administrador de la sede, o un usuario delegado de esa sede pueden gestionar esta plantilla",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Org admin: cualquier plantilla de su organización (org-wide o
     * de cualquier sede). Branch admin/org user delegado: solo la
     * plantilla de SU sede — nunca la org-wide (esa es decisión de
     * organización, no de sede).
     */
    public boolean canManage(DocumentTemplate template) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchTemplate =
                template.getBranch() != null
                        && currentBranchId != null
                        && template.getBranch().getId().equals(currentBranchId);

        if (!ownBranchTemplate) {
            return false;
        }

        return authContext.isCurrentBranchAdmin() || hasPermission("EDIT");
    }

    /**
     * Sede con la que se crea/edita una plantilla: el org admin
     * puede elegir libremente (org-wide con null, o una sede
     * puntual — ver requestedBranchId); branch admin y org user
     * delegado SIEMPRE usan la sede de su contexto actual, sin
     * importar lo que venga en el request.
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
        return authContext.isCurrentOrganizationAdmin()
                || authContext.isCurrentBranchAdmin();
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

    private Exceptions forbidden(String action) {
        return new Exceptions(
                "No tiene permisos para " + action + ".",
                HttpStatus.FORBIDDEN
        );
    }
}
