package pe.dcs.app.features.smallgroup;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.SmallGroup;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Grupos Pequeños / Células: a diferencia de Matrimonios
 * (admin-only), acá un org user normal puede ser "líder" de su
 * propio grupo — mismo mecanismo de delegación que
 * {@link pe.dcs.app.features.event.impl.EventAccessGuard}.
 *
 * - Org admin: crea/gestiona todos los grupos de su organización.
 * - Branch admin: crea/gestiona todos los grupos de su sede.
 * - Org user delegado (módulo SMALL_GROUP asignado vía Usuarios de
 *   Acceso): puede crear si tiene CREATE; solo GESTIONA (editar,
 *   agregar/quitar participantes) el grupo que él mismo creó, y
 *   solo si tiene EDIT.
 */
@Component
@RequiredArgsConstructor
public class SmallGroupAccessGuard {

    private static final String MODULE_CODE = "SMALL_GROUP";

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

        throw forbidden("crear grupos pequeños");
    }

    /**
     * ¿Puede usar el módulo (listar/ver)? Admin siempre; org user
     * delegado con cualquier permiso en SMALL_GROUP.
     */
    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!permissions().isEmpty()) {
            return;
        }

        throw forbidden("acceder a grupos pequeños");
    }

    public void assertCanManage(SmallGroup group) {

        if (!canManage(group)) {
            throw new Exceptions(
                    "Solo el administrador de la sede/organización o quien creó el grupo pueden gestionarlo",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Org admin/branch admin de la sede del grupo: gestionan todo.
     * Org user delegado: solo el grupo que él mismo creó, y solo
     * con permiso EDIT.
     */
    public boolean canManage(SmallGroup group) {

        UUID organizationId = group.getBranch().getOrganization().getId();
        UUID branchId = group.getBranch().getId();

        if (authContext.canManageOrgOrBranchOnly(organizationId, branchId)
                || authContext.isSystem()) {
            return true;
        }

        UUID currentUserId = authContext.getUserId();

        return hasPermission("EDIT")
                && group.getCreatedBy() != null
                && currentUserId != null
                && group.getCreatedBy().getId().equals(currentUserId);
    }

    public void assertSameOrganization(SmallGroup group) {

        UUID organizationId = group.getBranch().getOrganization().getId();

        if (!authContext.isSystem()
                && !organizationId.equals(authContext.getCurrentOrganizationId())) {

            throw new Exceptions(
                    "No tiene acceso a este grupo",
                    HttpStatus.FORBIDDEN
            );
        }
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
