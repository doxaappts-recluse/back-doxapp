package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.BibleEnrollment;
import pe.dcs.app.util.enums.bible_academy.BibleEnrollmentStatus;

import java.util.UUID;

@Repository
public interface BibleEnrollmentRepository extends JpaRepository<BibleEnrollment, UUID>, JpaSpecificationExecutor<BibleEnrollment> {

    long countByBibleClassId(UUID bibleClassId);

    boolean existsByBibleClassIdAndPersonId(UUID bibleClassId, UUID personId);

    /**
     * Prerequisito: ¿esta persona tiene un APPROVED en ALGÚN dictado
     * (de cualquier sede) de este curso puntual de la malla? Ver
     * BibleAcademyServiceImpl.hasApprovedPrerequisite — la malla es
     * de toda la organización, no de una sede.
     */
    boolean existsByPersonIdAndBibleClass_Course_IdAndStatus(UUID personId, UUID courseId, BibleEnrollmentStatus status);

    /**
     * Conteos org/sede-scoped para el Dashboard Ejecutivo (Reportes
     * Avanzados) — BibleEnrollmentSpecification no sirve para esto
     * porque está rooteada por dictado puntual (bibleClassId), no
     * por organización/sede, así que acá se agrega el único par de
     * métodos nuevos que Reportes Avanzados necesita para este
     * módulo (ver AdvancedReportsServiceImpl).
     */
    long countByStatusAndBibleClass_Branch_Id(BibleEnrollmentStatus status, UUID branchId);

    long countByStatusAndBibleClass_Branch_Organization_Id(BibleEnrollmentStatus status, UUID organizationId);
}
