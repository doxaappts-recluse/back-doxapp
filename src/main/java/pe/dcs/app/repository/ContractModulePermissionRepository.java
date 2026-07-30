package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ContractModulePermission;
import pe.dcs.app.entity.Permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractModulePermissionRepository extends JpaRepository<ContractModulePermission, UUID> {

    // =========================================================
    // PERMISOS DE UN MODULO EN UN CONTRATO
    // =========================================================

    @Query("""
        SELECT p.code
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        JOIN cmp.contractModule cm
        WHERE cm.contract.id = :contractId
          AND cm.module.id = :moduleId
          AND cm.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    List<String> findPermissions(
            @Param("contractId") UUID contractId,
            @Param("moduleId") UUID moduleId
    );

    /**
     * Igual que findPermissions, pero UNIENDO los permisos de
     * varios contratos a la vez (DISTINCT). Una sede puede tener
     * simultáneamente un contrato activo de ORGANIZATION y otro de
     * BRANCH: el sidebar debe mostrar, por módulo, la unión de lo
     * que cada uno habilita (p.ej. módulo 1 con permisos 2,3,4 en
     * el contrato de organización + permiso 1 en el de sede =
     * módulo 1 con 1,2,3,4), no los de un solo contrato.
     */
    @Query("""
        SELECT DISTINCT p.code
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        JOIN cmp.contractModule cm
        WHERE cm.contract.id IN :contractIds
          AND cm.module.id = :moduleId
          AND cm.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    List<String> findPermissionsByContractIds(
            @Param("contractIds") List<UUID> contractIds,
            @Param("moduleId") UUID moduleId
    );

    // =========================================================
    // VALIDAR PERMISO
    // =========================================================

    @Query("""
        SELECT COUNT(cmp) > 0
        FROM ContractModulePermission cmp
        JOIN cmp.contractModule cm
        JOIN cmp.permission p
        WHERE cm.contract.id = :contractId
          AND cm.module.id = :moduleId
          AND p.code = :permissionCode
          AND cm.status = 'ACTIVE'
          AND p.status = 'ACTIVE'
    """)
    boolean existsPermission(
            @Param("contractId") UUID contractId,
            @Param("moduleId") UUID moduleId,
            @Param("permissionCode") String permissionCode
    );

    // =========================================================
    // LISTADO COMPLETO
    // =========================================================

    @Query("""
        SELECT cmp
        FROM ContractModulePermission cmp
        JOIN FETCH cmp.permission p
        JOIN FETCH cmp.contractModule cm
        WHERE cm.contract.id = :contractId
    """)
    List<ContractModulePermission> findByContractId(
            @Param("contractId") UUID contractId
    );

    // =========================================================
    // ADMINISTRACION
    // =========================================================

    void deleteByContractModuleId(
            UUID contractModuleId
    );

    Optional<ContractModulePermission>
    findByContractModuleIdAndPermissionId(
            UUID contractModuleId,
            UUID permissionId
    );

    @Query("""
        SELECT COUNT(cmp) > 0
        FROM ContractModulePermission cmp
        WHERE cmp.contractModule.id = :contractModuleId
          AND cmp.permission.code = :permissionCode
    """)
    boolean existsByContractModuleIdAndPermissionCode(
            @Param("contractModuleId") UUID contractModuleId,
            @Param("permissionCode") String permissionCode
    );


    @Query("""
        SELECT p.code
        FROM ContractModulePermission cmp
        JOIN cmp.permission p
        WHERE cmp.contractModule.id = :contractModuleId
    """)
    List<String> findPermissionCodesByContractModuleId(
            @Param("contractModuleId") UUID contractModuleId
    );

}