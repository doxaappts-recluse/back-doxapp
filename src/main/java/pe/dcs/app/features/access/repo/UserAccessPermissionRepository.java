package pe.dcs.app.features.access.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.dcs.app.features.access.domain.UserAccessPermission;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccessPermissionRepository extends JpaRepository<UserAccessPermission, UUID> {

    List<UserAccessPermission> findByAccessId(UUID accessId);

    List<UserAccessPermission> findByAccessIdIn(Collection<UUID> accessIds);

    /** Borrado inmediato (evita el orden insert-antes-de-delete de Hibernate al reemplazar la delegación). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from UserAccessPermission p where p.accessId = :accessId")
    void deleteByAccessId(UUID accessId);

    Optional<UserAccessPermission> findByAccessIdAndModuleCode(UUID accessId, String moduleCode);
}
