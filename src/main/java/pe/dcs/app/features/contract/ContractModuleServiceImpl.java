package pe.dcs.app.features.contract;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.ContractModule;
import pe.dcs.app.entity.ContractModulePermission;
import pe.dcs.app.entity.Module;
import pe.dcs.app.entity.Permission;
import pe.dcs.app.features.contract.request.ContractModuleRequest;
import pe.dcs.app.features.contract.response.ContractModuleConfigResponse;
import pe.dcs.app.features.contract.response.ContractPermissionConfigResponse;
import pe.dcs.app.features.contract.service.ContractModuleService;
import pe.dcs.app.repository.ContractModulePermissionRepository;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.PermissionRepository;
import pe.dcs.app.security.service.AuthContext;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.StatusType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractModuleServiceImpl implements ContractModuleService {

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final ContractModuleRepository contractModuleRepository;
    private final ContractModulePermissionRepository contractModulePermissionRepository;
    private final AuthContext authContext;

    // =====================================================
    // CATALOGO
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public List<ContractModuleConfigResponse> getCatalog(UUID contractId) {

        if (!authContext.isSystem()) {
            throw new Exceptions(
                    "error.soloAdministradorSistemaPuedeGestionarContratos",
                    HttpStatus.FORBIDDEN
            );
        }

        /*
         * El catálogo de un contrato es de módulos TENANT
         * (los que puede llegar a usar un org/branch admin).
         * Los módulos exclusivos de SYSTEM (visibleOrgAdmin=
         * false y visibleBranchAdmin=false) no tiene sentido
         * ofrecerlos para armar el contrato de un tenant.
         */
        List<Module> leafModules =
                moduleRepository.findAllActive()
                        .stream()
                        .filter(m -> m.getChildren().isEmpty())
                        .filter(ContractModuleServiceImpl::isTenantVisible)
                        .toList();

        List<Permission> permissions =
                permissionRepository.findByStatusOrderByNameAsc(
                        StatusType.ACTIVE
                );

        Set<UUID> assignedModuleIds = Set.of();
        Map<UUID, Set<UUID>> assignedPermissionsByModule = Map.of();

        if (contractId != null) {

            assignedModuleIds =
                    contractModuleRepository
                            .findActiveByContractId(contractId)
                            .stream()
                            .map(cm -> cm.getModule().getId())
                            .collect(Collectors.toSet());

            assignedPermissionsByModule =
                    contractModulePermissionRepository
                            .findByContractId(contractId)
                            .stream()
                            .collect(
                                    Collectors.groupingBy(
                                            cmp ->
                                                    cmp.getContractModule()
                                                            .getModule()
                                                            .getId(),
                                            Collectors.mapping(
                                                    cmp -> cmp.getPermission().getId(),
                                                    Collectors.toSet()
                                            )
                                    )
                            );
        }

        List<ContractModuleConfigResponse> catalog =
                new java.util.ArrayList<>();

        for (Module module : leafModules) {

            Set<UUID> assignedPermissions =
                    assignedPermissionsByModule.getOrDefault(
                            module.getId(),
                            Set.of()
                    );

            ContractModuleConfigResponse response =
                    new ContractModuleConfigResponse();

            response.setModuleId(module.getId());
            response.setCode(module.getCode());
            response.setName(module.getLocalizedName());

            if (module.getParent() != null) {
                response.setParentId(module.getParent().getId());
                response.setParentName(module.getParent().getLocalizedName());
            }

            response.setAssigned(
                    assignedModuleIds.contains(module.getId())
            );

            response.setPermissions(
                    permissions.stream()
                            .map(p ->
                                    new ContractPermissionConfigResponse(
                                            p.getId(),
                                            p.getCode(),
                                            p.getName(),
                                            assignedPermissions.contains(p.getId())
                                    )
                            )
                            .toList()
            );

            catalog.add(response);
        }

        return catalog;
    }

    private static boolean isTenantVisible(Module module){

        boolean visibleOrgAdmin =
                !Boolean.FALSE.equals(module.getVisibleOrgAdmin());

        boolean visibleBranchAdmin =
                !Boolean.FALSE.equals(module.getVisibleBranchAdmin());

        return visibleOrgAdmin || visibleBranchAdmin;
    }

    // =====================================================
    // REEMPLAZO COMPLETO
    // =====================================================

    @Override
    @Transactional
    public void replaceModules(
            Contract contract,
            List<ContractModuleRequest> modules
    ) {

        List<ContractModule> existing =
                contractModuleRepository.findByContractId(
                        contract.getId()
                );

        if (!existing.isEmpty()) {

            for (ContractModule contractModule : existing) {

                contractModulePermissionRepository
                        .deleteByContractModuleId(
                                contractModule.getId()
                        );
            }

            contractModuleRepository.deleteAll(existing);

            /*
             * Flush obligatorio acá. Sin esto, Hibernate encola los
             * DELETE de arriba y los INSERT de assignModules() en la
             * misma unidad de trabajo, y su orden de ejecución por
             * defecto es INSERTS antes que DELETES (sin importar el
             * orden en que se llamó al repositorio). Si un módulo se
             * mantiene igual entre la versión vieja y la nueva (caso
             * normal: editar un contrato sin tocar sus módulos), el
             * INSERT de reemplazo llega a la BD ANTES que el DELETE
             * del que reemplaza y choca contra uk_contract_module
             * (contract_id, module_id), porque la fila vieja todavía
             * existe en ese instante.
             */
            contractModulePermissionRepository.flush();
            contractModuleRepository.flush();
        }

        assignModules(contract, modules);
    }

    private void assignModules(
            Contract contract,
            List<ContractModuleRequest> modules
    ) {

        if (modules == null) {
            return;
        }

        for (ContractModuleRequest request : modules) {

            Module module = getLeafModuleOrThrow(
                    request.getModuleId()
            );

            ContractModule contractModule = new ContractModule();

            contractModule.setContract(contract);
            contractModule.setModule(module);
            contractModule.enable();

            contractModuleRepository.save(contractModule);

            Set<UUID> permissionIds =
                    request.getPermissionIds() != null
                            ? new LinkedHashSet<>(request.getPermissionIds())
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

                ContractModulePermission cmp =
                        new ContractModulePermission();

                cmp.setContractModule(contractModule);
                cmp.setPermission(permission);

                contractModulePermissionRepository.save(cmp);
            }
        }
    }

    private Module getLeafModuleOrThrow(UUID moduleId) {

        if (moduleId == null) {
            throw new Exceptions(
                    "error.moduloInvalido",
                    HttpStatus.BAD_REQUEST
            );
        }

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() ->
                        new Exceptions(
                                "error.moduloNoEncontrado",
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!module.isActive() || !module.getChildren().isEmpty()) {
            throw new Exceptions(
                    "error.soloPuedenAsignarModulosHojaActivos",
                    HttpStatus.BAD_REQUEST
            );
        }

        return module;
    }

}
