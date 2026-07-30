package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.ContractModuleResolver;
import pe.dcs.app.features.module.ContractResolver;
import pe.dcs.app.features.module.mapper.SidebarMapper;
import pe.dcs.app.features.module.response.MeAccessResponse;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserAccessModuleRepository;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SidebarService {

    private final ContractResolver contractResolver;
    private final ContractModuleRepository contractModuleRepository;
    private final UserAccessModuleRepository userModuleRepository;
    private final ModuleRepository moduleRepository;
    private final SidebarMapper sidebarMapper;

    @Cacheable(
            value = "sidebar",
            key = "#auth.principal.userId + '-' + #auth.principal.currentBranchId"
    )
    public MeAccessResponse getSidebar(Authentication auth){

        CredentialDetailsImpl user = (CredentialDetailsImpl) auth.getPrincipal();

        MeAccessResponse response = new MeAccessResponse();

        // =====================================================
        // SYSTEM
        // =====================================================

        if (user.isSystemAdmin() || user.isSystemSupport()) {

            response.setAccessType(
                    user.isSystemAdmin()
                            ? RoleType.SYSTEM_ADMIN
                            : RoleType.SYSTEM_SUPPORT
            );

            List<Module> systemVisible =
                    moduleRepository.findAllActive()
                            .stream()
                            .filter(m -> !Boolean.FALSE.equals(m.getVisibleSystem()))
                            .toList();

            response.setModules(
                    sidebarMapper.toTreeForSystem(
                            systemVisible
                    )
            );

            return response;
        }

        // =====================================================
        // CONTEXTO ACTUAL
        // =====================================================

        UUID organizationId = user.getCurrentOrganizationId();

        UUID branchId = user.getCurrentBranchId();

        if(organizationId == null || branchId == null){
            response.setAccessType(RoleType.UNKNOWN);
            response.setModules(List.of());
            return response;
        }

        // =====================================================
        // CONTRATOS APLICABLES
        // =====================================================

        List<Contract> contracts = contractResolver.getActiveContractsByBranch(branchId);

        if(contracts.isEmpty()){
            response.setAccessType(RoleType.NO_CONTRACT);
            response.setModules(List.of());
            return response;
        }

        // =====================================================
        // MODULOS DISPONIBLES POR CONTRATO
        // =====================================================

        Set<UUID> contractModuleIds =
                contracts.stream()
                        .flatMap(
                                contract ->
                                        contractModuleRepository
                                                .findModuleIdsByContractId(
                                                        contract.getId()
                                                )
                                                .stream()
                        )
                        .collect(
                                Collectors.toSet()
                        );


        // =====================================================
        // MODULOS BASE
        // =====================================================

        List<Module> modules = moduleRepository.findAllActive();

        /*
         * Una sede puede tener un contrato de ORGANIZATION Y otro
         * propio de BRANCH activos al mismo tiempo (contracts ya
         * trae ambos, ver ContractResolver.getActiveContractsByBranch
         * -> findActiveContractsForBranch). Los permisos por módulo
         * deben ser la UNIÓN de lo que habilita cada uno, no solo
         * el primero: se pasa la lista completa, no un solo id.
         */
        List<UUID> contractIds =
                contracts.stream()
                        .map(Contract::getId)
                        .toList();

        // =====================================================
        // ORGANIZATION ADMIN
        // =====================================================

        if(user.hasOrganizationAdminAccess(organizationId)){

            response.setAccessType(RoleType.ORG_ADMIN);

            List<Module> orgAdminVisible =
                    modules.stream()
                            .filter(m -> !Boolean.FALSE.equals(m.getVisibleOrgAdmin()))
                            .toList();

            response.setModules(
                    sidebarMapper.toTree(
                            orgAdminVisible,
                            contractModuleIds,
                            contractIds,
                            null
                    )
            );

            return response;
        }

        // =====================================================
        // BRANCH ADMIN
        // =====================================================

        if(user.hasBranchAdminAccess(organizationId, branchId)){

            response.setAccessType(RoleType.ORG_BRANCH_ADMIN);

            List<Module> branchAdminVisible =
                    modules.stream()
                            .filter(m -> !Boolean.FALSE.equals(m.getVisibleBranchAdmin()))
                            .toList();

            response.setModules(
                    sidebarMapper.toTree(
                            branchAdminVisible,
                            contractModuleIds,
                            contractIds,
                            null
                    )
            );

            return response;
        }

        // =====================================================
        // ORGANIZATION USER
        // =====================================================

        if(user.hasOrganizationUser(organizationId, branchId)){

            Set<UUID> userModules =
                    new HashSet<>(
                            userModuleRepository
                                    .findActiveModuleIdsByPersonIdAndOrganizationIdAndBranchId(
                                            user.getUserId(),
                                            organizationId,
                                            branchId,
                                            StatusType.ACTIVE
                                    )
                    );

            /*
             *
             * Seguridad:
             *
             * Usuario solo puede ver:
             *
             * 1. Lo asignado personalmente EN ESTA sede/organización
             *    (una persona puede tener otro UserAccess en otra
             *    sede, con otros módulos delegados: no deben mezclarse)
             * 2. Lo permitido por contrato
             *
             */

            userModules.retainAll(contractModuleIds);

            response.setAccessType(RoleType.ORG_USER);

            List<Module> userVisible =
                    modules.stream()
                            .filter(m -> !Boolean.FALSE.equals(m.getVisibleUser()))
                            .toList();

            response.setModules(
                    sidebarMapper.toTree(
                            userVisible,
                            userModules,
                            null,
                            user.getUserId(),
                            organizationId,
                            branchId
                    )
            );

            return response;
        }

        // =====================================================
        // UNKNOWN
        // =====================================================

        response.setAccessType(RoleType.UNKNOWN);
        response.setModules(List.of());
        return response;
    }

    @CacheEvict(value = "sidebar", allEntries = true)
    public void clearCache(){
    }

}