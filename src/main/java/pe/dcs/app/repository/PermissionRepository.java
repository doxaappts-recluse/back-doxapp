package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Permission;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    @Query("""
        SELECT p.code
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        JOIN cmp.contractModule cm
        WHERE cm.contract.id = :contractId
          AND cm.module.id IN :moduleIds
    """)
    List<String> findPermissions(
            @Param("contractId") UUID contractId,
            @Param("moduleIds") List<UUID> moduleIds
    );

    @Query("""
        SELECT cm.module.id
        FROM ContractModule cm
        JOIN UserAccessModule uam
             ON uam.module.id = cm.module.id
        WHERE cm.contract.id = :contractId
          AND uam.userAccess.person.id = :personId
          AND cm.status = :status
          AND uam.status = :status
          AND uam.enabled = true
          AND uam.userAccess.active = :status
    """)
    List<UUID> findAllowedModuleIds(
            @Param("contractId") UUID contractId,
            @Param("personId") UUID personId,
            @Param("status") StatusType status
    );

}