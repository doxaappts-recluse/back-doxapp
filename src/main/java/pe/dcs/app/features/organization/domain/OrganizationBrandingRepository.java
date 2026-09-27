package pe.dcs.app.features.organization.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationBrandingRepository extends JpaRepository<OrganizationBranding, UUID> {
}
