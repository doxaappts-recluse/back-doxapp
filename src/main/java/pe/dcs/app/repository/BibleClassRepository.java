package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.BibleClass;

import java.util.UUID;

@Repository
public interface BibleClassRepository extends JpaRepository<BibleClass, UUID>, JpaSpecificationExecutor<BibleClass> {

    long countByCourseId(UUID courseId);
}
