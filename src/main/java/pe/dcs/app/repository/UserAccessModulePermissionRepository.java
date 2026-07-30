package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserAccessModulePermission;
import pe.dcs.app.util.enums.StatusType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserAccessModulePermissionRepository extends JpaRepository<UserAccessModulePermission, UUID> {

    /**
     * Permisos delegados a una persona para un módulo, acotados al
     * acceso puntual (organización + sede) actualmente activo. Sin
     * este filtro, un permiso delegado bajo el acceso de UNA sede
     * podía "filtrarse" hacia el sidebar de OTRA sede de la misma
     * persona.
     */
    @Query("""
        SELECT p.code
        FROM UserAccessModulePermission ump
        JOIN ump.userAccessModule uam
        JOIN ump.permission p
        WHERE uam.userAccess.person.id = :personId
          AND uam.userAccess.organization.id = :organizationId
          AND uam.userAccess.branch.id = :branchId
          AND uam.module.id = :moduleId
          AND uam.enabled = true
          AND uam.userAccess.active = :status
    """)
    List<String> findPermissionsByAccessContext(
            @Param("personId") UUID personId,
            @Param("organizationId") UUID organizationId,
            @Param("branchId") UUID branchId,
            @Param("moduleId") UUID moduleId,
            @Param("status") StatusType status
    );

    List<UserAccessModulePermission> findByUserAccessModuleId(
            UUID userAccessModuleId
    );

    boolean existsByUserAccessModuleUserAccessPersonIdAndUserAccessModuleModuleIdAndPermissionCode(
            UUID personId,
            UUID moduleId,
            String permissionCode
    );

    // =========================================================
    // ADMINISTRACION (asignación de accesos)
    // =========================================================

    void deleteByUserAccessModuleIdIn(
            Collection<UUID> userAccessModuleIds
    );

}