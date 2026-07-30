package pe.dcs.app.features.module.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Permission;
import pe.dcs.app.repository.ContractModulePermissionRepository;
import pe.dcs.app.repository.PermissionRepository;
import pe.dcs.app.repository.UserAccessModulePermissionRepository;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ContractModulePermissionRepository contractModulePermissionRepository;
    private final UserAccessModulePermissionRepository userModulePermissionRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Permisos del módulo según el/los contrato(s) activos de la
     * sede actual — puede ser uno (solo BRANCH, o solo
     * ORGANIZATION) o dos a la vez (una sede puede tener un
     * contrato de organización Y uno propio de sede activos
     * simultáneamente). Cuando son dos, el resultado es la UNIÓN
     * de lo que cada uno habilita para ese módulo, no el de uno
     * solo. Lo usan SYSTEM, ORG_ADMIN y BRANCH_ADMIN.
     */
    @Transactional(readOnly = true)
    public List<String> getPermissions(
            List<UUID> contractIds,
            UUID moduleId
    ) {

        if (contractIds == null || contractIds.isEmpty()) {
            return List.of();
        }

        return contractModulePermissionRepository.findPermissionsByContractIds(
                contractIds,
                moduleId
        );
    }

    /**
     * Permisos específicos del usuario, acotados al acceso
     * (organización + sede) actualmente activo.
     * Solo ORG_USER.
     */
    @Transactional(readOnly = true)
    public List<String> getUserPermissions(
            UUID userId,
            UUID organizationId,
            UUID branchId,
            UUID moduleId
    ) {

        if (userId == null) {
            return List.of();
        }

        return userModulePermissionRepository.findPermissionsByAccessContext(
                userId,
                organizationId,
                branchId,
                moduleId,
                StatusType.ACTIVE
        );
    }

    /**
     * Catálogo completo de códigos de permiso activos.
     * Lo usa SYSTEM (SYSTEM_ADMIN/SYSTEM_SUPPORT): no administra
     * bajo un contrato, así que no tiene sentido acotarlo a
     * contract_module_permissions. Ve siempre el máximo permitido
     * en cada módulo del sistema.
     */
    @Transactional(readOnly = true)
    public List<String> getAllPermissionCodes() {

        return permissionRepository.findByStatusOrderByNameAsc(StatusType.ACTIVE)
                .stream()
                .map(Permission::getCode)
                .toList();
    }

}