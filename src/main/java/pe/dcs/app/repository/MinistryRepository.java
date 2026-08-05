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
    List<Ministry> findAllByStatusOrderByNameEsAsc(
            StatusType status
    );

    /**
     * Usado por SmallGroupServiceImpl/BibleAcademyServiceImpl para
     * el find-or-create de ministerios de referencia (uk_ministry_code
     * garantiza unicidad global por code, estable sin importar el
     * idioma de nameEs/nameEn).
     */
    Optional<Ministry> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}