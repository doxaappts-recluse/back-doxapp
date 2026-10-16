package pe.dcs.app.features.access.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pe.dcs.app.features.access.domain.PermissionProfile;

import java.util.Optional;
import java.util.UUID;

public interface PermissionProfileRepository extends JpaRepository<PermissionProfile, UUID>, JpaSpecificationExecutor<PermissionProfile> {

    Optional<PermissionProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("select count(p) > 0 from PermissionProfile p where p.organizationId = :orgId and lower(p.name) = lower(:name) and (:excludeId is null or p.id <> :excludeId)")
    boolean nameTaken(UUID orgId, String name, UUID excludeId);
}
