package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.entity.Permission;

import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    @Query("""
        SELECT p.code
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        JOIN cmp.contractModule cm
        WHERE cm.contract.id = :contractId
          AND cm.module.id IN :moduleIds
    """)
    List<String> findPermissions(UUID contractId, List<UUID> moduleIds);

    @Query("""
        SELECT cm.module.id
        FROM ContractModule cm
        JOIN UserModule um ON um.module.id = cm.module.id
        WHERE cm.contract.id = :contractId
        AND um.user.id = :userId
        AND cm.status = 'ACTIVE'
        AND um.status = 'ACTIVE'
    """)
    List<UUID> findAllowedModuleIds(UUID contractId, UUID userId);

}