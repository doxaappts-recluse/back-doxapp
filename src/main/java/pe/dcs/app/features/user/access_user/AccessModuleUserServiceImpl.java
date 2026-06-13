package pe.dcs.app.features.user.access_user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.*;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.user.access_user.request.UserAccessUpsertRequest;
import pe.dcs.app.features.user.access_user.request.UserModuleAccessRequest;
import pe.dcs.app.features.user.access_user.response.*;
import pe.dcs.app.features.user.access_user.service.AccessModuleUserService;
import pe.dcs.app.repository.*;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.contract.ContractStatus;
import pe.dcs.app.util.enums.StatusType;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccessModuleUserServiceImpl implements AccessModuleUserService {

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final ContractModuleRepository contractModuleRepository;
    private final ContractModulePermissionRepository contractModulePermissionRepository;

    private final UserModuleRepository userModuleRepository;
    private final UserModulePermissionRepository userModulePermissionRepository;

    private final AuthContext authContext;

    // =========================================================
    // GET CONFIG
    // =========================================================

    @Override
    public UserAccessConfigResponse getConfig(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exceptions("User not found", HttpStatus.NOT_FOUND));

        assertSameOrganization(user);

        Contract contract = contractRepository
                .findFirstByOrganizationIdAndStatusOrderByStartDateDesc(
                        authContext.getOrganizationId(),
                        ContractStatus.ACTIVE
                )
                .orElseThrow(() -> new Exceptions("No active contract", HttpStatus.BAD_REQUEST));

        // =========================
        // USER MODULES
        // =========================
        List<UserModule> userModules =
                userModuleRepository.findAllByUserId(userId);

        // módulos asignados
        Set<UUID> assignedModuleIds = userModules.stream()
                .map(m -> m.getModule().getId())
                .collect(Collectors.toSet());

        // ids de UserModule
        Set<UUID> userModuleIds = userModules.stream()
                .map(UserModule::getId)
                .collect(Collectors.toSet());

        // =========================
        // USER ASSIGNED PERMISSIONS
        // =========================
        Map<UUID, Set<UUID>> assignedPermissionsByModule =
                userModules.stream()
                        .collect(Collectors.toMap(
                                userModule -> userModule.getModule().getId(),
                                userModule -> userModulePermissionRepository
                                        .findByUserModuleId(userModule.getId())
                                        .stream()
                                        .map(p -> p.getPermission().getId())
                                        .collect(Collectors.toSet())
                        ));

        // =========================
        // CONTRACT MODULES
        // =========================
        List<ContractModule> contractModules =
                contractModuleRepository.findByContractId(contract.getId());

        // =========================
        // CONTRACT PERMISSIONS
        // =========================
        List<ContractModulePermission> permissions =
                contractModulePermissionRepository
                        .findByContractModule_Contract_Id(contract.getId());

        Map<UUID, List<PermissionConfigResponse>> permissionsByModule =
                permissions.stream()
                        .collect(Collectors.groupingBy(
                                p -> p.getContractModule().getModule().getId(),
                                Collectors.mapping(permission -> {

                                    UUID moduleId =
                                            permission.getContractModule()
                                                    .getModule()
                                                    .getId();

                                    boolean assigned =
                                            assignedPermissionsByModule
                                                    .getOrDefault(moduleId, Set.of())
                                                    .contains(
                                                            permission.getPermission()
                                                                    .getId()
                                                    );

                                    return new PermissionConfigResponse(
                                            permission.getPermission().getId(),
                                            permission.getPermission().getCode(),
                                            assigned
                                    );

                                }, Collectors.toList())
                        ));

        // =========================
        // BUILD RESPONSE
        // =========================
        List<ModuleAccessConfigResponse> modules = contractModules.stream()
                .map(cm -> {

                    UUID moduleId = cm.getModule().getId();

                    return new ModuleAccessConfigResponse(
                            moduleId,
                            cm.getModule().getName(),
                            assignedModuleIds.contains(moduleId),
                            permissionsByModule.getOrDefault(moduleId, List.of())
                    );
                })
                .toList();

        return new UserAccessConfigResponse(
                new UserSummaryResponse(
                        user.getId(),
                        user.getCredential() != null
                                ? user.getCredential().getUsername()
                                : null,
                        user.getName(),
                        user.getLastname()
                ),
                contract.getId(),
                modules,
                modules.stream()
                        .filter(ModuleAccessConfigResponse::assigned)
                        .toList()
        );
    }

    // =========================================================
    // UPSERT
    // =========================================================

    @Override
    public void upsertAccess(UUID userId, UserAccessUpsertRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exceptions("User not found", HttpStatus.NOT_FOUND));

        assertSameOrganization(user);

        Contract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> new Exceptions("Contract not found", HttpStatus.NOT_FOUND));

        if (!contract.isActive()) {
            throw new Exceptions("Contract is not active", HttpStatus.BAD_REQUEST);
        }

        List<UserModule> existing = userModuleRepository.findAllByUserId(userId);

        Map<UUID, UserModule> existingMap = existing.stream()
                .collect(Collectors.toMap(
                        m -> m.getModule().getId(),
                        m -> m
                ));

        Set<UUID> incomingModuleIds = request.modules()
                .stream()
                .map(UserModuleAccessRequest::moduleId)
                .collect(Collectors.toSet());

        // ================================
        // MODULES TO DELETE
        // ================================
        List<UserModule> toDelete = existing.stream()
                .filter(m -> !incomingModuleIds.contains(m.getModule().getId()))
                .toList();

        if (!toDelete.isEmpty()) {

            List<UUID> userModuleIds = toDelete.stream()
                    .map(UserModule::getId)
                    .toList();

            List<UserModulePermission> permissionsToDelete =
                    userModulePermissionRepository.findAllByUserModuleIdIn(userModuleIds);

            userModulePermissionRepository.deleteAll(permissionsToDelete);
            userModuleRepository.deleteAll(toDelete);
        }

        // ================================
        // UPSERT MODULES
        // ================================
        for (UserModuleAccessRequest req : request.modules()) {

            UserModule userModule = existingMap.get(req.moduleId());

            if (userModule == null) {
                userModule = new UserModule();
                userModule.setUser(user);

                Module moduleRef = new Module();
                moduleRef.setId(req.moduleId());

                userModule.setModule(moduleRef);
            }

            userModule.setEnabled(true);
            userModule.setStatus(StatusType.ACTIVE);

            userModule = userModuleRepository.save(userModule);

            syncPermissions(userModule, req.permissionIds());
        }
    }

    // =========================================================
    // SYNC PERMISSIONS
    // =========================================================

    private void syncPermissions(UserModule userModule, List<UUID> incomingPermissions) {

        if (incomingPermissions == null) incomingPermissions = List.of();

        List<UserModulePermission> existing =
                userModulePermissionRepository.findByUserModuleId(userModule.getId());

        Set<UUID> incomingSet = new HashSet<>(incomingPermissions);

        // DELETE REMOVED
        List<UserModulePermission> toDelete = existing.stream()
                .filter(p -> !incomingSet.contains(p.getPermission().getId()))
                .toList();

        userModulePermissionRepository.deleteAll(toDelete);

        Set<UUID> existingIds = existing.stream()
                .map(p -> p.getPermission().getId())
                .collect(Collectors.toSet());

        for (UUID permissionId : incomingPermissions) {

            if (existingIds.contains(permissionId)) continue;

            UserModulePermission ump = new UserModulePermission();
            ump.setUserModule(userModule);

            Permission ref = new Permission();
            ref.setId(permissionId);

            ump.setPermission(ref);

            userModulePermissionRepository.save(ump);
        }
    }

    // =========================================================
    // SECURITY CHECK
    // =========================================================

    private void assertSameOrganization(User target) {

        UUID actorOrgId = authContext.getOrganizationId();

        if (target.getOrganization() == null ||
                !target.getOrganization().getId().equals(actorOrgId)) {

            throw new Exceptions(
                    "User does not belong to your organization",
                    HttpStatus.FORBIDDEN
            );
        }
    }
}