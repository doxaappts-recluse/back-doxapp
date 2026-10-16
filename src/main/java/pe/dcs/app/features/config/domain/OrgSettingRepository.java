package pe.dcs.app.features.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrgSettingRepository extends JpaRepository<OrgSetting, UUID> {

    List<OrgSetting> findByOrganizationId(UUID organizationId);

    List<OrgSetting> findByOrganizationIdAndNamespace(UUID organizationId, String namespace);

    Optional<OrgSetting> findByOrganizationIdAndBranchIdAndNamespaceAndKey(UUID organizationId, UUID branchId, String namespace, String key);

    Optional<OrgSetting> findByOrganizationIdAndBranchIdIsNullAndNamespaceAndKey(UUID organizationId, String namespace, String key);
}
