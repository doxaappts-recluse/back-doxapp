package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.UserModulePermission;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserModulePermissionRepository extends JpaRepository<UserModulePermission, UUID> {

    @Query("""
        SELECT p.code
        FROM UserModulePermission ump
        JOIN ump.permission p
        JOIN ump.userModule um
        WHERE um.user.id = :userId
          AND um.module.id = :moduleId
          AND um.enabled = true
    """)
    List<String> findPermissions(
            @Param("userId") UUID userId,
            @Param("moduleId") UUID moduleId
    );

    @Query("""
        SELECT ump
        FROM UserModulePermission ump
        JOIN ump.userModule um
        WHERE um.user.id = :userId
          AND um.module.id = :moduleId
    """)
    List<UserModulePermission> findByUserAndModule(
            @Param("userId") UUID userId,
            @Param("moduleId") UUID moduleId
    );

    boolean existsByUserModuleUserIdAndUserModuleModuleIdAndPermissionCode(
            UUID userId,
            UUID moduleId,
            String permissionCode
    );


}