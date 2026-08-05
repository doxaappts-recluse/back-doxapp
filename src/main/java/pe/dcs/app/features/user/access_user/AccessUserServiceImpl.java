package pe.dcs.app.features.user.access_user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.response.ContractModuleAccessResponse;
import pe.dcs.app.features.module.response.ContractModulePermissionOptionResponse;
import pe.dcs.app.features.module.service.ContractModuleAccessService;
import pe.dcs.app.features.user.access_user.mapper.AccessUserMapper;
import pe.dcs.app.features.user.access_user.request.AccessUserListRequest;
import pe.dcs.app.features.user.access_user.request.AccessUserModuleRequest;
import pe.dcs.app.features.user.access_user.request.AccessUserUpdateRequest;
import pe.dcs.app.features.user.access_user.response.AccessUserConfigResponse;
import pe.dcs.app.features.user.access_user.response.AccessUserResponse;
import pe.dcs.app.features.user.access_user.service.AccessUserService;
import pe.dcs.app.features.user.shared.UserChangePasswordRequest;
import pe.dcs.app.repository.*;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;
import pe.dcs.app.util.enums.resolveSort.AccessUserSort;
import pe.dcs.app.util.pagination.PageResponse;
import pe.dcs.app.util.pagination.PageableUtil;
import pe.dcs.app.util.pagination.PaginationResponse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * El usuario (Person + Credential + UserAccess ORG_USER) ya
 * existe, creado por otro flujo (org-admin-branch cubre
 * ORG_ADMIN/ORG_BRANCH_ADMIN; la creación de Person/ORG_USER
 * queda pendiente para más adelante). Este service solo
 * gestiona: listar esos accesos, ver/editar sus módulos y
 * permisos por módulo, habilitar/deshabilitar credencial y
 * cambiar contraseña.
 *
 * Todas las operaciones se indexan por el id del ACCESO
 * (UserAccess), no por el id de la persona: una persona puede
 * tener varios accesos ORG_USER (uno por sede), y cada uno se
 * gestiona de forma independiente (módulos delegados distintos
 * por sede). Enable/disable/cambio de contraseña siguen
 * operando sobre la Credential de la persona dueña del acceso,
 * porque la credencial es 1:1 con la persona, no por acceso.
 */
@Service
@RequiredArgsConstructor
public class AccessUserServiceImpl implements AccessUserService {

    private final UserAccessRepository userAccessRepository;
    private final CredentialRepository credentialRepository;
    private final UserAccessModuleRepository userAccessModuleRepository;
    private final UserAccessModulePermissionRepository userAccessModulePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ModuleRepository moduleRepository;

    private final PasswordEncoder passwordEncoder;
    private final AccessUserMapper mapper;
    private final AuthContext authContext;
    private final ContractModuleAccessService contractModuleAccessService;

    // =====================================================
    // SEARCH
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccessUserResponse> search(AccessUserListRequest request) {

        assertCallerIsOrgOrBranchAdmin();

        Pageable pageable =
                PageableUtil.buildPageable(
                        request.getPagination(),
                        request.getSorts(),
                        AccessUserSort::resolvePath
                );

        Page<UserAccess> page =
                userAccessRepository.findAll(
                        AccessUserSpecification.filter(
                                request,
                                authContext
                        ),
                        pageable
                );

        boolean showAudit = authContext.canViewAudit();

        return new PageResponse<>(
                page.getContent()
                        .stream()
                        .map(access -> mapper.toResponse(access, showAudit))
                        .toList(),

                new PaginationResponse(
                        (int) page.getTotalElements(),
                        page.getTotalPages(),
                        page.getSize(),
                        page.getNumber()
                )
        );
    }

    // =====================================================
    // GET BY ID (datos + catálogo con "assigned")
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public AccessUserConfigResponse getById(UUID id) {

        assertCallerIsOrgOrBranchAdmin();

        UserAccess access = findOrgUserAccessOrThrow(id);

        validateAccess(access);

        UUID branchId =
                access.getBranch() != null
                        ? access.getBranch().getId()
                        : null;

        List<ContractModuleAccessResponse> available =
                contractModuleAccessService.getAvailableModules(branchId);

        List<UserAccessModule> assignedModules =
                userAccessModuleRepository.findByUserAccessId(access.getId());

        Set<UUID> assignedModuleIds =
                assignedModules.stream()
                        .filter(
                                uam ->
                                        uam.getStatus() == StatusType.ACTIVE
                                                &&
                                                Boolean.TRUE.equals(uam.getEnabled())
                        )
                        .map(uam -> uam.getModule().getId())
                        .collect(Collectors.toSet());

        Map<UUID, Set<UUID>> assignedPermissionsByModule = new HashMap<>();

        for (UserAccessModule uam : assignedModules) {

            List<UserAccessModulePermission> permissions =
                    userAccessModulePermissionRepository.findByUserAccessModuleId(
                            uam.getId()
                    );

            assignedPermissionsByModule.put(
                    uam.getModule().getId(),
                    permissions.stream()
                            .map(p -> p.getPermission().getId())
                            .collect(Collectors.toSet())
            );
        }

        return mapper.toConfigResponse(
                access,
                available,
                assignedModuleIds,
                assignedPermissionsByModule
        );
    }

    // =====================================================
    // UPDATE (solo módulos/permisos, reemplazo completo)
    // =====================================================

    @Override
    @Transactional
    public void update(UUID id, AccessUserUpdateRequest request) {

        assertCallerIsOrgOrBranchAdmin();

        UserAccess access = findOrgUserAccessOrThrow(id);

        validateAccess(access);

        UUID branchId =
                access.getBranch() != null
                        ? access.getBranch().getId()
                        : null;

        Map<UUID, Set<UUID>> allowed =
                resolveAllowedModulePermissions(branchId);

        validateModules(request.getModules(), allowed);

        replaceModules(access, request.getModules());
    }

    // =====================================================
    // ENABLE / DISABLE
    // =====================================================

    @Override
    @Transactional
    public void enable(UUID id) {

        assertCallerIsOrgOrBranchAdmin();

        UserAccess access = findOrgUserAccessOrThrow(id);

        validateAccess(access);

        Credential credential = requireCredential(access.getPerson());

        credential.setStatus(StatusType.ACTIVE);

        credentialRepository.save(credential);
    }

    @Override
    @Transactional
    public void disable(UUID id) {

        assertCallerIsOrgOrBranchAdmin();

        UserAccess access = findOrgUserAccessOrThrow(id);

        validateAccess(access);

        Credential credential = requireCredential(access.getPerson());

        credential.setStatus(StatusType.INACTIVE);

        credentialRepository.save(credential);
    }

    // =====================================================
    // CHANGE PASSWORD
    // =====================================================

    @Override
    @Transactional
    public void changePassword(UUID id, UserChangePasswordRequest request) {

        assertCallerIsOrgOrBranchAdmin();

        UserAccess access = findOrgUserAccessOrThrow(id);

        validateAccess(access);

        Credential credential = requireCredential(access.getPerson());

        String newUsername = request.getUsername();

        if (newUsername != null
                && !newUsername.isBlank()
                && !newUsername.equals(credential.getUsername())) {

            validateUsernameForUpdate(
                    credential.getId(),
                    newUsername
            );

            credential.setUsername(newUsername);
        }

        credential.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        credentialRepository.save(credential);
    }

    // =====================================================
    // MODULOS: asignar / reemplazar
    // =====================================================

    private void assignModules(
            UserAccess access,
            List<AccessUserModuleRequest> modules
    ) {

        if (modules == null) {
            return;
        }

        for (AccessUserModuleRequest moduleRequest : modules) {

            Module module =
                    getChildModuleOrThrow(moduleRequest.getModuleId());

            UserAccessModule userAccessModule = new UserAccessModule();

            userAccessModule.setUserAccess(access);
            userAccessModule.setModule(module);
            userAccessModule.setStatus(StatusType.ACTIVE);
            userAccessModule.setEnabled(true);

            userAccessModuleRepository.save(userAccessModule);

            Set<UUID> permissionIds =
                    moduleRequest.getPermissionIds() != null
                            ? new LinkedHashSet<>(moduleRequest.getPermissionIds())
                            : Set.of();

            for (UUID permissionId : permissionIds) {

                Permission permission =
                        permissionRepository.findById(permissionId)
                                .orElseThrow(() ->
                                        new Exceptions(
                                                "error.permisoNoEncontrado",
                                                HttpStatus.NOT_FOUND
                                        )
                                );

                UserAccessModulePermission userAccessModulePermission =
                        new UserAccessModulePermission();

                userAccessModulePermission.setUserAccessModule(userAccessModule);
                userAccessModulePermission.setPermission(permission);

                userAccessModulePermissionRepository.save(
                        userAccessModulePermission
                );
            }
        }
    }

    private void replaceModules(
            UserAccess access,
            List<AccessUserModuleRequest> modules
    ) {

        List<UserAccessModule> existing =
                userAccessModuleRepository.findByUserAccessId(
                        access.getId()
                );

        if (!existing.isEmpty()) {

            List<UUID> existingIds =
                    existing.stream()
                            .map(UserAccessModule::getId)
                            .toList();

            userAccessModulePermissionRepository
                    .deleteByUserAccessModuleIdIn(existingIds);

            userAccessModuleRepository.deleteAll(existing);
        }

        assignModules(access, modules);
    }

    private Module getChildModuleOrThrow(UUID moduleId) {

        /*
         * La existencia/estado/jerarquía ya se validó contra
         * el catálogo del contrato en validateModules(); acá
         * solo se vuelve a resolver la entidad para persistir.
         */
        return moduleRepository.findById(moduleId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.moduloNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    // =====================================================
    // VALIDACIONES
    // =====================================================

    private void assertCallerIsOrgOrBranchAdmin() {

        if (!authContext.isCurrentOrganizationAdmin()
                && !authContext.isCurrentBranchAdmin()) {

            throw new Exceptions(
                    "error.soloAdministradorOrganizacionSedePuedeGestionar",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * Autoriza si el llamante puede administrar este acceso
     * puntual (su organización/sede).
     */
    private void validateAccess(UserAccess access) {

        if (access.getActive() != StatusType.ACTIVE) {
            throw new Exceptions(
                    "error.accesoNoActivo",
                    HttpStatus.CONFLICT
            );
        }

        boolean canManage =
                authContext.canAccess(
                        access.getOrganization() != null
                                ? access.getOrganization().getId()
                                : null,
                        access.getBranch() != null
                                ? access.getBranch().getId()
                                : null
                );

        if (!canManage) {
            throw new Exceptions(
                    "error.noTienePermisosAdministrarUsuario",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    /**
     * Busca el acceso por su propio id (no el de la persona) y
     * valida que sea de tipo ORG_USER: este feature solo gestiona
     * módulos delegados sobre accesos de usuario de operación, no
     * sobre accesos ORG_ADMIN/ORG_BRANCH_ADMIN (esos reciben sus
     * módulos automáticamente por contrato, ver SidebarService).
     */
    private UserAccess findOrgUserAccessOrThrow(UUID id) {

        UserAccess access =
                userAccessRepository.findById(id)
                        .orElseThrow(() ->
                                new Exceptions(
                                        "error.accesoNoEncontrado",
                                        HttpStatus.NOT_FOUND
                                )
                        );

        if (!access.isOrganizationUser()) {
            throw new Exceptions(
                    "error.accesoNoTipoUsuarioOrganizacion",
                    HttpStatus.CONFLICT
            );
        }

        return access;
    }

    /**
     * Módulos hijos + permisos que el contrato activo de la
     * sede permite asignar, en forma de mapa para validar rápido.
     */
    private Map<UUID, Set<UUID>> resolveAllowedModulePermissions(
            UUID branchId
    ) {

        return contractModuleAccessService.getAvailableModules(branchId)
                .stream()
                .collect(
                        Collectors.toMap(
                                ContractModuleAccessResponse::getModuleId,
                                m ->
                                        m.getPermissions()
                                                .stream()
                                                .map(ContractModulePermissionOptionResponse::getId)
                                                .collect(Collectors.toSet())
                        )
                );
    }

    /**
     * Valida que cada módulo enviado:
     * - no esté repetido
     * - sea un módulo HIJO habilitado por el contrato activo
     * Y que cada permiso enviado para ese módulo esté
     * habilitado por el contrato para ese mismo módulo.
     */
    private void validateModules(
            List<AccessUserModuleRequest> modules,
            Map<UUID, Set<UUID>> allowed
    ) {

        if (modules == null || modules.isEmpty()) {
            return;
        }

        Set<UUID> seenModules = new HashSet<>();

        for (AccessUserModuleRequest moduleRequest : modules) {

            if (moduleRequest.getModuleId() == null) {
                throw new Exceptions(
                        "error.moduloInvalido",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (!seenModules.add(moduleRequest.getModuleId())) {
                throw new Exceptions(
                        "error.moduloRepetidoSolicitud",
                        HttpStatus.BAD_REQUEST
                );
            }

            Set<UUID> allowedPermissions =
                    allowed.get(moduleRequest.getModuleId());

            if (allowedPermissions == null) {
                throw new Exceptions(
                        "error.soloPuedenAsignarModulosHijosHabilitados",
                        HttpStatus.BAD_REQUEST
                );
            }

            List<UUID> permissionIds =
                    moduleRequest.getPermissionIds() != null
                            ? moduleRequest.getPermissionIds()
                            : List.of();

            for (UUID permissionId : permissionIds) {

                if (!allowedPermissions.contains(permissionId)) {
                    throw new Exceptions(
                            "error.permisoNoDisponibleModuloContratoActivo",
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
        }
    }

    /**
     * El username es único a nivel global (todas las credenciales),
     * no solo dentro del acceso/organización actual.
     */
    private void validateUsernameForUpdate(UUID credentialId, String username) {

        if (credentialRepository.existsByUsernameAndIdNot(username, credentialId)) {
            throw new Exceptions(
                    "error.elUsuarioYaExiste",
                    HttpStatus.CONFLICT
            );
        }
    }

    private Credential requireCredential(Person person) {

        Credential credential = person.getCredential();

        if (credential == null) {
            throw new Exceptions(
                    "error.usuarioNoTieneCredencial",
                    HttpStatus.CONFLICT
            );
        }

        return credential;
    }

}
