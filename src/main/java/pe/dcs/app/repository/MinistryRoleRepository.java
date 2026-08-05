package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MinistryRoleRepository extends JpaRepository<MinistryRole, UUID>, JpaSpecificationExecutor<MinistryRole> {
    List<MinistryRole> findAllByStatusOrderByNameEsAsc(StatusType status);

    /**
     * Usado por SmallGroupServiceImpl/BibleAcademyServiceImpl para
     * el find-or-create de roles de referencia (uk_ministry_role_code
     * garantiza unicidad por ministerio+code).
     */
    Optional<MinistryRole> findByMinistryIdAndCode(UUID ministryId, String code);

    boolean existsByMinistryIdAndCodeIgnoreCase(UUID ministryId, String code);

    boolean existsByMinistryIdAndCodeIgnoreCaseAndIdNot(UUID ministryId, String code, UUID id);
}