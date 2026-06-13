package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ContractModulePermission;
import pe.dcs.app.entity.Permission;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractModulePermissionRepository extends JpaRepository<ContractModulePermission, UUID> {

    // =========================================
    // CHECK PERMISSION BY MODULE + CODE
    // =========================================
    @Query("""
        SELECT CASE WHEN COUNT(cmp) > 0 THEN true ELSE false END
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        WHERE cmp.contractModule.id = :contractModuleId
          AND p.code = :permissionCode
    """)
    boolean existsByContractModuleIdAndPermissionCode(
            @Param("contractModuleId") UUID contractModuleId,
            @Param("permissionCode") String permissionCode
    );

    @Query("""
        SELECT p.code
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        JOIN cmp.contractModule cm
        WHERE cm.contract.id = :contractId
          AND cm.module.id = :moduleId
    """)
    List<String> findPermissions(UUID contractId, UUID moduleId);

    List<ContractModulePermission> findByContractModuleId(UUID contractModuleId);

    List<ContractModulePermission> findByContractModuleIdIn(
            List<UUID> contractModuleIds
    );

    List<ContractModulePermission> findByContractModule_Contract_Id(UUID contractId);

}