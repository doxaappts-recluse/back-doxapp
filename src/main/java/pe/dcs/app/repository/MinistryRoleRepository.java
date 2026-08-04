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
    List<MinistryRole> findAllByStatusOrderByNameAsc(StatusType status);

    /**
     * Usado por SmallGroupServiceImpl para el find-or-create del rol
     * "Líder de Grupo Pequeño" dentro del ministerio de referencia
     * (uk_ministry_role_name garantiza unicidad por ministerio+nombre).
     */
    Optional<MinistryRole> findByMinistryIdAndName(UUID ministryId, String name);
}