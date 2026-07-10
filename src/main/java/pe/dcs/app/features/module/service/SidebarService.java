package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.entity.Module;
import pe.dcs.app.features.module.ContractResolver;
import pe.dcs.app.features.module.mapper.SidebarMapper;
import pe.dcs.app.features.module.response.MeAccessResponse;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserModuleRepository;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.enums.SystemRoleType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SidebarService {

    private final ContractResolver contractResolver;
    private final ContractModuleRepository contractModuleRepository;
    private final UserModuleRepository userModuleRepository;
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

        if(user.isSystem()){

            response.setAccessType(SystemRoleType.SYSTEM);

            response.setModules(
                    sidebarMapper.toTree(
                            moduleRepository.findAllActive(),
                            null,
                            null,
                            null
                    )
            );

            return response;
        }

        // =====================================================
        // CONTEXTO
        // =====================================================

        UUID organizationId = user.getCurrentOrganizationId();

        UUID branchId = user.getCurrentBranchId();

        if(organizationId == null || branchId == null){
            response.setAccessType(SystemRoleType.UNKNOWN);
            response.setModules(List.of());
            return response;
        }

        // =====================================================
        // CONTRATO ACTIVO
        // =====================================================

        Contract contract = contractResolver.getActiveContract(branchId);

        if(contract == null){
            response.setAccessType(SystemRoleType.NO_CONTRACT);
            response.setModules(List.of());
            return response;
        }

        // =====================================================
        // CARGAR UNA SOLA VEZ
        // =====================================================

        List<Module> modules = moduleRepository.findAllActive();

        Set<UUID> contractModules =
                new HashSet<>(
                        contractModuleRepository
                                .findModuleIdsByContractId(
                                        contract.getId()
                                )
                );

        // =====================================================
        // ORGANIZATION ADMIN
        // =====================================================

        if(user.hasOrganizationAdminAccess(organizationId)){

            response.setAccessType(SystemRoleType.ORG_ADMIN);

            response.setModules(
                    sidebarMapper.toTree(
                            modules,
                            contractModules,
                            contract.getId(),
                            null
                    )
            );

            return response;
        }

        // =====================================================
        // BRANCH ADMIN
        // =====================================================

        if(user.hasBranchAdminAccess(organizationId, branchId)){

            response.setAccessType(SystemRoleType.ORG_BRANCH_ADMIN);

            response.setModules(
                    sidebarMapper.toTree(
                            modules,
                            contractModules,
                            contract.getId(),
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
                                    .findActiveModuleIdsByUserId(
                                            user.getUserId()
                                    )
                    );

            /*
             * Seguridad:
             *
             * El usuario solamente podrá
             * visualizar módulos que:
             *
             * 1. Están asignados al usuario.
             * 2. Existen en el contrato activo.
             */

            userModules.retainAll(contractModules);

            response.setAccessType(SystemRoleType.ORG_USER);

            response.setModules(
                    sidebarMapper.toTree(
                            modules,
                            userModules,
                            contract.getId(),
                            user.getUserId()
                    )
            );

            return response;
        }

        // =====================================================
        // UNKNOWN
        // =====================================================

        response.setAccessType(SystemRoleType.UNKNOWN);
        response.setModules(List.of());

        return response;
    }

    @CacheEvict(value = "sidebar", allEntries = true)
    public void clearCache(){
    }

}