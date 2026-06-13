package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.entity.UserModulePermission;

import java.util.List;
import java.util.UUID;

public interface UserModulePermissionRepository extends JpaRepository<UserModulePermission, UUID> {

    @Query("""
        SELECT ump
        FROM UserModulePermission ump
        WHERE ump.userModule.id = :userModuleId
    """)
    List<UserModulePermission> findByUserModuleId(UUID userModuleId);

    List<UserModulePermission> findAllByUserModuleIdIn(List<UUID> userModuleIds);
}