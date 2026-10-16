package pe.dcs.app.features.config.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrgHolidayRepository extends JpaRepository<OrgHoliday, UUID> {

    List<OrgHoliday> findByOrganizationIdOrderByDateAsc(UUID organizationId);

    @Modifying
    @Query("delete from OrgHoliday h where h.organizationId = :org")
    int deleteAllOf(@Param("org") UUID org);
}
