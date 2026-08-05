package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.ContractModule;
import pe.dcs.app.entity.ContractModulePermission;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.Permission;
import pe.dcs.app.features.module.ContractResolver;
import pe.dcs.app.features.module.response.ContractModuleAccessResponse;
import pe.dcs.app.features.module.response.ContractModulePermissionOptionResponse;
import pe.dcs.app.repository.ContractModulePermissionRepository;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resuelve los módulos HIJOS y sus permisos habilitados
 * por el contrato ACTIVO de la organización/sede.
 *
 * Ya devuelve el listado filtrado, así que el que lo
 * consume (formulario de asignación de accesos, etc.)
 * no tiene que volver a validar nada: lo que llega
 * de acá es exactamente lo asignable.
 *
 * Solo lo usan ORG_ADMIN / ORG_BRANCH_ADMIN. SYSTEM no
 * tiene organización/sede de contexto, así que no aplica.
 */
@Service
@RequiredArgsConstructor
public class ContractModuleAccessService {

    private final ContractResolver contractResolver;
    private final ContractModuleRepository contractModuleRepository;
    private final ContractModulePermissionRepository contractModulePermissionRepository;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public List<ContractModuleAccessResponse> getAvailableModules(
            UUID requestedBranchId
    ) {

        if (authContext.isSystem()) {
            throw new Exceptions(
                    "error.noDisponibleUsuariosSistema",
                    HttpStatus.FORBIDDEN
            );
        }

        UUID branchId = resolveBranchId(requestedBranchId);

        if (branchId == null) {
            throw new Exceptions(
                    "error.debeIndicarSede2",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<Contract> contracts =
                contractResolver.getActiveContractsByBranch(branchId);

        if (contracts.isEmpty()) {
            return List.of();
        }

        Map<UUID, ContractModuleAccessResponse> modules =
                new LinkedHashMap<>();

        for (Contract contract : contracts) {

            /*
             * Los módulos disponibles salen de ContractModule (lo
             * que el contrato habilitó), no de ContractModulePermission.
             * Así un módulo aparece igual aunque todavía no tenga
             * permisos configurados para ese contrato.
             */
            List<ContractModule> contractModules =
                    contractModuleRepository.findActiveByContractId(
                            contract.getId()
                    );

            if (contractModules.isEmpty()) {
                continue;
            }

            Map<UUID, List<ContractModulePermission>> permissionsByModule =
                    contractModulePermissionRepository
                            .findByContractId(contract.getId())
                            .stream()
                            .collect(
                                    Collectors.groupingBy(
                                            cmp ->
                                                    cmp.getContractModule()
                                                            .getModule()
                                                            .getId()
                                    )
                            );

            for (ContractModule contractModule : contractModules) {

                Module module = contractModule.getModule();

                if (module == null || !module.isActive()) {
                    continue;
                }

                /*
                 * "Módulo hijo" = módulo hoja, sin submódulos propios.
                 * Esto excluye los agrupadores de menú (ej. Administración,
                 * Personas), tengan o no parent_id, ya que lo que importa
                 * es si alguien más lo referencia como padre, no si él
                 * mismo tiene uno.
                 */
                if (!module.getChildren().isEmpty()) {
                    continue;
                }

                /*
                 * Solo se ofrecen para asignar a un ORG_USER los
                 * módulos marcados como visibleUser=true. Es un
                 * filtro estructural aparte del contrato: aunque
                 * el contrato lo habilite, si el módulo no está
                 * pensado para usuarios normales no aparece acá.
                 */
                if (Boolean.FALSE.equals(module.getVisibleUser())) {
                    continue;
                }

                ContractModuleAccessResponse response =
                        modules.computeIfAbsent(
                                module.getId(),
                                id -> {

                                    ContractModuleAccessResponse r =
                                            new ContractModuleAccessResponse();

                                    r.setModuleId(module.getId());
                                    r.setName(module.getLocalizedName());

                                    return r;
                                }
                        );

                List<ContractModulePermission> modulePermissions =
                        permissionsByModule.getOrDefault(
                                module.getId(),
                                List.of()
                        );

                for (ContractModulePermission cmp : modulePermissions) {

                    Permission permission = cmp.getPermission();

                    if (permission == null || !permission.isActive()) {
                        continue;
                    }

                    boolean alreadyAdded =
                            response.getPermissions()
                                    .stream()
                                    .anyMatch(
                                            p ->
                                                    p.getId()
                                                            .equals(
                                                                    permission.getId()
                                                            )
                                    );

                    if (!alreadyAdded) {

                        response.getPermissions()
                                .add(
                                        new ContractModulePermissionOptionResponse(
                                                permission.getId(),
                                                permission.getCode(),
                                                permission.getName()
                                        )
                                );
                    }
                }
            }
        }

        return List.copyOf(modules.values());
    }

    /**
     * ORG_BRANCH_ADMIN: siempre su propia sede, ignora lo que
     * pida el frontend (igual que en el resto de módulos).
     *
     * ORG_ADMIN: la sede que elija (puede administrar varias).
     */
    private UUID resolveBranchId(UUID requestedBranchId) {

        if (authContext.isCurrentBranchAdmin()) {
            return authContext.getCurrentBranchId();
        }

        return requestedBranchId;
    }

}
