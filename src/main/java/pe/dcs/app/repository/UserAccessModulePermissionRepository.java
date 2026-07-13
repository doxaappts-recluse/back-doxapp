package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserAccessModulePermission;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAccessModulePermissionRepository extends JpaRepository<UserAccessModulePermission, UUID> {

    @Query("""
        SELECT p.code
        FROM UserAccessModulePermission ump
        JOIN ump.userAccessModule uam
        JOIN ump.permission p
        WHERE uam.userAccess.person.id = :personId
        AND uam.module.id = :moduleId
        AND uam.enabled = true
        AND uam.userAccess.active = true
    """)
    List<String> findPermissions(
            @Param("personId") UUID personId,
            @Param("moduleId") UUID moduleId
    );

    @Query("""
        SELECT ump
        FROM UserAccessModulePermission ump
        JOIN ump.userAccessModule uam
        WHERE uam.userAccess.person.id = :personId
        AND uam.module.id = :moduleId
    """)
    List<UserAccessModulePermission> findByPersonAndModule(
            @Param("personId") UUID personId,
            @Param("moduleId") UUID moduleId
    );

    boolean existsByUserAccessModuleUserAccessPersonIdAndUserAccessModuleModuleIdAndPermissionCode(
            UUID personId,
            UUID moduleId,
            String permissionCode
    );

}