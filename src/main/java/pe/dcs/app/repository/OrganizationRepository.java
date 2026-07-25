package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.Organization;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {
    boolean existsByRuc(String ruc);

    boolean existsByRucAndIdNot(
            String ruc,
            UUID id
    );

    List<Organization> findByStatusOrderByNameAsc(
            StatusType status
    );
}