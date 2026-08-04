package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.BibleCurriculum;
import pe.dcs.app.util.enums.bible_academy.BibleCurriculumStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BibleCurriculumRepository extends JpaRepository<BibleCurriculum, UUID>, JpaSpecificationExecutor<BibleCurriculum> {

    Optional<BibleCurriculum> findByOrganizationIdAndStatus(UUID organizationId, BibleCurriculumStatus status);

    List<BibleCurriculum> findByOrganizationId(UUID organizationId);
}
