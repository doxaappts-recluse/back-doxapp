package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MinistryRepository extends JpaRepository<Ministry, UUID>, JpaSpecificationExecutor<Ministry> {
    List<Ministry> findAllByStatusOrderByNameAsc(
            StatusType status
    );

    /**
     * Usado por SmallGroupServiceImpl para el find-or-create del
     * ministerio de referencia "Grupos Pequeños" (uk_ministry_name
     * garantiza unicidad global por nombre).
     */
    Optional<Ministry> findByName(String name);
}