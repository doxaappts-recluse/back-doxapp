package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pe.dcs.app.entity.Contract;
import pe.dcs.app.features.module.response.MeAccessResponse;
import pe.dcs.app.repository.ContractModuleRepository;
import pe.dcs.app.repository.ModuleRepository;
import pe.dcs.app.repository.UserModuleRepository;
import pe.dcs.app.features.module.ContractResolver;
import pe.dcs.app.features.module.mapper.SidebarMapper;
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

    @Cacheable(value = "sidebar", key = "#auth.principal.userId + '-' + #auth.principal.organizationId")
    public MeAccessResponse getSidebar(Authentication auth) {

        CredentialDetailsImpl user =
                (CredentialDetailsImpl) auth.getPrincipal();

        MeAccessResponse response = new MeAccessResponse();

        // =========================================================
        // SYSTEM USERS
        // =========================================================
        if (user.isSystem()) {

            response.setAccessType(SystemRoleType.SYSTEM);

            response.setModules(
                    sidebarMapper.toTree(
                            moduleRepository.findAllActive(),
                            null,
                            null
                    )
            );

            return response;
        }

        // =========================================================
        // ORGANIZATION CONTEXT
        // =========================================================
        UUID orgId = user.getOrganizationId();

        Contract contract =
                contractResolver.getActiveContract(orgId);

        if (contract == null) {
            response.setAccessType(SystemRoleType.NO_CONTRACT);
            response.setModules(List.of());
            return response;
        }

        Set<UUID> contractModules =
                new HashSet<>(
                        contractModuleRepository.findModuleIdsByContractId(contract.getId())
                );

        // =========================================================
        // ORG_ADMIN
        // =========================================================
        if (user.isOrgAdmin()) {

            response.setAccessType(SystemRoleType.ORG_ADMIN);

            response.setModules(
                    sidebarMapper.toTree(
                            moduleRepository.findAllActive(),
                            contractModules,
                            contract.getId()
                    )
            );

            return response;
        }

        // =========================================================
        // ORG_USER
        // =========================================================
        if (user.isOrgUser()) {

            Set<UUID> userModules =
                    new HashSet<>(
                            userModuleRepository.findModuleIdsByUserId(user.getUserId())
                    );

            userModules.retainAll(contractModules);

            response.setAccessType(SystemRoleType.ORG_USER);

            response.setModules(
                    sidebarMapper.toTree(
                            moduleRepository.findAllActive(),
                            userModules,
                            contract.getId()
                    )
            );

            return response;
        }

        // =========================================================
        // FALLBACK
        // =========================================================
        response.setAccessType(SystemRoleType.UNKNOWN);
        response.setModules(List.of());

        return response;
    }

    @CacheEvict(value = "sidebar", allEntries = true)
    public void clearCache() {}
}