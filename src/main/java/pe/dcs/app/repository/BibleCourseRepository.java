package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.BibleCourse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BibleCourseRepository extends JpaRepository<BibleCourse, UUID>, JpaSpecificationExecutor<BibleCourse> {

    List<BibleCourse> findByCurriculumIdOrderByOrderAsc(UUID curriculumId);

    Optional<BibleCourse> findByCurriculumIdAndOrder(UUID curriculumId, Integer order);

    boolean existsByCurriculumIdAndOrder(UUID curriculumId, Integer order);
}
