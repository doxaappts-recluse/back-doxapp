package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface MinistryRoleRepository extends JpaRepository<MinistryRole, UUID>, JpaSpecificationExecutor<MinistryRole> {
    List<MinistryRole> findAllByStatusOrderByNameAsc(StatusType status);
}