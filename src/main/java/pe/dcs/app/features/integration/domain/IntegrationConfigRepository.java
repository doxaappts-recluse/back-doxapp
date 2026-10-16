package pe.dcs.app.features.integration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, UUID> {

    List<IntegrationConfig> findByOrganizationId(UUID organizationId);

    Optional<IntegrationConfig> findByOrganizationIdAndProvider(UUID organizationId, IntegrationProvider provider);

    Optional<IntegrationConfig> findByOrganizationIdAndKindAndStatus(UUID organizationId, String kind, String status);
}
