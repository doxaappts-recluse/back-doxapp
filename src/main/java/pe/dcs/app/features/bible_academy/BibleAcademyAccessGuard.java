package pe.dcs.app.features.bible_academy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.dcs.app.entity.BibleClass;
import pe.dcs.app.entity.BibleCourse;
import pe.dcs.app.entity.BibleCurriculum;
import pe.dcs.app.entity.BibleEnrollment;
import pe.dcs.app.entity.Module;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

/**
 * Guard de Academia Bíblica. SYSTEM queda completamente fuera —
 * mismo criterio que {@link pe.dcs.app.features.document_template.DocumentTemplateAccessGuard}
 * (no es su rol interferir en la Academia Bíblica de una
 * organización, ver memoria del usuario). Dos niveles de autoridad
 * bien distintos:
 *
 * - Malla curricular (BibleCurriculum) y los cursos que cuelgan de
 *   ella (BibleCourse.curriculum != null): EXCLUSIVO de org admin,
 *   ni siquiera branch admin ni delegado — es compartida entre todas
 *   las sedes, por eso solo la organización la gestiona.
 * - Cursos extra (BibleCourse.curriculum == null), dictados
 *   (BibleClass) y matrículas (BibleEnrollment): org admin, branch
 *   admin, u org user delegado (módulo BIBLE_ACADEMY con permiso
 *   CREATE/EDIT vía Usuarios de Acceso) — siempre acotado a su
 *   propia sede, mismo mecanismo que SmallGroupAccessGuard.
 * - Saltarse el prerequisito de un nivel de malla al matricular:
 *   SOLO admin (org/sede), nunca un delegado — ver
 *   assertCanOverridePrerequisite.
 */
@Component
@RequiredArgsConstructor
public class BibleAcademyAccessGuard {

    private static final String MODULE_CODE = "BIBLE_ACADEMY";

    private final AuthContext authContext;
    private final ModuleRepository moduleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;

    // =========================================================
    // MALLA CURRICULAR — EXCLUSIVO ORG ADMIN
    // =========================================================

    public void assertCanManageCurriculum() {
        if (!authContext.isCurrentOrganizationAdmin()) {
            throw forbidden("action.gestionarMallaCurricularSoloAdministradorOrganizacion");
        }
    }

    public void assertSameOrganizationCurriculum(BibleCurriculum curriculum) {

        UUID organizationId = curriculum.getOrganization().getId();

        if (!organizationId.equals(authContext.getCurrentOrganizationId())) {
            throw new Exceptions("error.noTieneAccesoMallaCurricular", HttpStatus.FORBIDDEN);
        }
    }

    // =========================================================
    // USO GENERAL DEL MÓDULO (listar)
    // =========================================================

    public void assertCanUse() {

        if (isAdmin()) {
            return;
        }

        if (!permissions().isEmpty()) {
            return;
        }

        throw forbidden("action.accederAcademiaBiblica");
    }

    // =========================================================
    // CURSOS (malla y extra)
    // =========================================================

    public void assertCanCreateExtraCourse() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("action.crearCursosExtra");
    }

    /** Curso de malla: mismo criterio exclusivo que la malla. Curso extra: mismo criterio que un dictado de esa sede. */
    public boolean canManageCourse(BibleCourse course) {

        if (!course.isExtra()) {
            return authContext.isCurrentOrganizationAdmin();
        }

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchCourse =
                course.getBranch() != null
                        && currentBranchId != null
                        && course.getBranch().getId().equals(currentBranchId);

        if (!ownBranchCourse) {
            return false;
        }

        return authContext.isCurrentBranchAdmin() || hasPermission("EDIT");
    }

    public void assertCanManageCourse(BibleCourse course) {
        if (!canManageCourse(course)) {
            throw forbidden("action.gestionarEsteCurso");
        }
    }

    // =========================================================
    // DICTADOS (BibleClass)
    // =========================================================

    public void assertCanCreateClass() {

        if (isAdmin()) {
            return;
        }

        if (hasPermission("CREATE")) {
            return;
        }

        throw forbidden("action.abrirDictados");
    }

    public boolean canManageClass(BibleClass bibleClass) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return true;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean ownBranchClass =
                bibleClass.getBranch() != null
                        && currentBranchId != null
                        && bibleClass.getBranch().getId().equals(currentBranchId);

        if (!ownBranchClass) {
            return false;
        }

        return authContext.isCurrentBranchAdmin() || hasPermission("EDIT");
    }

    public void assertCanManageClass(BibleClass bibleClass) {
        if (!canManageClass(bibleClass)) {
            throw forbidden("action.gestionarEsteDictado");
        }
    }

    // =========================================================
    // MATRÍCULAS (BibleEnrollment)
    // =========================================================

    public boolean canManageEnrollment(BibleEnrollment enrollment) {
        return canManageClass(enrollment.getBibleClass());
    }

    public void assertCanManageEnrollment(BibleEnrollment enrollment) {
        if (!canManageEnrollment(enrollment)) {
            throw forbidden("action.gestionarEstaMatricula");
        }
    }

    /**
     * Saltarse el prerequisito de un nivel de malla: SOLO admin
     * (org admin, o branch admin de la sede del dictado destino),
     * nunca un delegado — es una excepción pastoral, no una
     * operación delegable.
     */
    public void assertCanOverridePrerequisite(BibleClass targetClass) {

        if (authContext.isCurrentOrganizationAdmin()) {
            return;
        }

        UUID currentBranchId = authContext.getCurrentBranchId();

        boolean isBranchAdminOfTarget =
                authContext.isCurrentBranchAdmin()
                        && targetClass.getBranch() != null
                        && currentBranchId != null
                        && targetClass.getBranch().getId().equals(currentBranchId);

        if (!isBranchAdminOfTarget) {
            throw forbidden("action.saltarPrerequisitoMatriculaRequiereAdministrador");
        }
    }

    // =========================================================
    // SEDE
    // =========================================================

    /**
     * Igual patrón que DocumentTemplateAccessGuard/MarriageServiceImpl.resolveBranch:
     * org admin elige libremente, cualquier otro rol queda ligado a
     * su sede actual.
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

    private Exceptions forbidden(String actionKey) {

        org.springframework.context.MessageSource messageSource = pe.dcs.app.util.MessageSourceHolder.get();

        String action = messageSource != null
                ? messageSource.getMessage(actionKey, null, actionKey, org.springframework.context.i18n.LocaleContextHolder.getLocale())
                : actionKey;

        return new Exceptions("error.noTienePermisosPara", HttpStatus.FORBIDDEN, action);
    }
}
