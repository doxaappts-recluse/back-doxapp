package pe.dcs.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.dcs.app.entity.EventRegistration;
import pe.dcs.app.util.enums.events.RegistrationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID>, JpaSpecificationExecutor<EventRegistration> {

    boolean existsByEventIdAndUserId(
            UUID eventId,
            UUID userId
    );

    long countByEventIdAndStatus(
            UUID eventId,
            RegistrationStatus status
    );

    Optional<EventRegistration> findByIdAndEvent_Organization_Id(
            UUID id,
            UUID organizationId
    );

    @EntityGraph(attributePaths = {
            "event",
            "user"
    })
    Page<EventRegistration> findAll(
            Specification<EventRegistration> spec,
            Pageable pageable
    );

    boolean existsByEventIdAndUserIdAndStatusNot(
            UUID eventId,
            UUID userId,
            RegistrationStatus status
    );

    Optional<EventRegistration> findByQrToken(String qrToken);

    boolean existsByQrToken(String qrToken);

    long countByEventId(UUID eventId);

    @Query(value = """
        SELECT
            CAST((r.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/LIMA') AS date),
            SUM(CASE WHEN r.status = 'REGISTERED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END)
        FROM event_registrations r
        WHERE r.event_id = :eventId
        GROUP BY CAST((r.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/LIMA') AS date)
        ORDER BY CAST((r.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'America/LIMA') AS date)
    """, nativeQuery = true)
    List<Object[]> registrationReport(UUID eventId);

    @Query(value = """
        SELECT
            CAST((r.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Lima' AS date),
            SUM(CASE WHEN r.status = 'REGISTERED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END)
        FROM event_registrations r
        WHERE r.event_id = :eventId
        GROUP BY CAST((r.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Lima' AS date)
        ORDER BY CAST((r.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'America/Lima' AS date)
    """, nativeQuery = true)
    List<Object[]> occupancyReport(@Param("eventId") UUID eventId);

}