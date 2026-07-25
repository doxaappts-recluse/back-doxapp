package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.repository.ContractModulePermissionRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ContractModulePermissionRepository contractModulePermissionRepository;
    private final UserAccessModulePermissionRepository userModulePermissionRepository;

    /**
     * Permisos del módulo según el contrato.
     * Lo usan SYSTEM, ORG_ADMIN y BRANCH_ADMIN.
     */
    @Transactional(readOnly = true)
    public List<String> getPermissions(
            UUID contractId,
            UUID moduleId
    ) {

        if (contractId == null) {
            return List.of();
        }

        return contractModulePermissionRepository.findPermissions(
                contractId,
                moduleId
        );
    }

    /**
     * Permisos específicos del usuario.
     * Solo ORG_USER.
     */
    @Transactional(readOnly = true)
    public List<String> getUserPermissions(
            UUID userId,
            UUID moduleId
    ) {

        if (userId == null) {
            return List.of();
        }

        return userModulePermissionRepository.findPermissions(
                userId,
                moduleId,
                StatusType.ACTIVE
        );
    }

}