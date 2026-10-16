package pe.dcs.app.features.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistedAccessGrantRepository extends JpaRepository<AssistedAccessGrant, UUID> {

    Optional<AssistedAccessGrant> findByStaffIdAndOrganizationIdAndStatus(UUID staffId, UUID organizationId, String status);

    List<AssistedAccessGrant> findByCaseIdOrderByRequestedAtDesc(UUID caseId);

    List<AssistedAccessGrant> findByStatusAndExpiresAtBefore(String status, Instant cutoff);

    List<AssistedAccessGrant> findByOrganizationIdAndRequestedAtAfter(UUID organizationId, Instant since);

    List<AssistedAccessGrant> findByOrganizationIdOrderByRequestedAtDesc(UUID organizationId);
}
