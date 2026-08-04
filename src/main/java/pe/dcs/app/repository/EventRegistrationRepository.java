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

import java.math.BigDecimal;
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

    /**
     * Ingresos por inscripciones pagadas (paymentStatus=PAID,
     * excluyendo canceladas). Estadística independiente del Balance
     * de Finanzas — deliberadamente no se suma ahí para evitar
     * doble conteo, ver EventDashboardServiceImpl.
     */
    @Query("""
        SELECT COALESCE(SUM(r.finalPrice), 0)
        FROM EventRegistration r
        WHERE r.event.id = :eventId
          AND r.paymentStatus = 'PAID'
          AND r.status <> 'CANCELLED'
    """)
    BigDecimal sumRegistrationIncome(@Param("eventId") UUID eventId);

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

    @Query(value = """
        SELECT age_range, COUNT(*)
        FROM (
            SELECT
                CASE
                    WHEN EXTRACT(YEAR FROM AGE(CURRENT_DATE, birth_date)) BETWEEN 0 AND 12 THEN '0-12'
                    WHEN EXTRACT(YEAR FROM AGE(CURRENT_DATE, birth_date)) BETWEEN 13 AND 16 THEN '13-16'
                    WHEN EXTRACT(YEAR FROM AGE(CURRENT_DATE, birth_date)) BETWEEN 17 AND 25 THEN '17-25'
                    ELSE '26+'
                END AS age_range
            FROM event_registrations
            WHERE event_id = :eventId
              AND birth_date IS NOT NULL
        ) x
        GROUP BY age_range
        ORDER BY
            CASE age_range
                WHEN '0-12' THEN 1
                WHEN '13-16' THEN 2
                WHEN '17-25' THEN 3
                ELSE 4
            END
    """, nativeQuery = true)
    List<Object[]> ageReport(@Param("eventId") UUID eventId);

    /**
     * Mezcla de categorías entre las inscripciones activas (no
     * canceladas): cuántos Miembro/Staff/Visitante/Invitado/Becado.
     */
    @Query("""
        SELECT r.category, COUNT(r)
        FROM EventRegistration r
        WHERE r.event.id = :eventId
          AND r.status <> 'CANCELLED'
        GROUP BY r.category
    """)
    List<Object[]> categoryReport(@Param("eventId") UUID eventId);

    /**
     * Pagado vs Pendiente entre las inscripciones activas: cantidad
     * y monto (finalPrice) por estado de pago.
     */
    @Query("""
        SELECT r.paymentStatus, COUNT(r), COALESCE(SUM(r.finalPrice), 0)
        FROM EventRegistration r
        WHERE r.event.id = :eventId
          AND r.status <> 'CANCELLED'
        GROUP BY r.paymentStatus
    """)
    List<Object[]> paymentStatusReport(@Param("eventId") UUID eventId);

    /**
     * Inscripciones activas por sede. Solo tiene sentido mostrarlo
     * en el front cuando el evento es scope=ORGANIZATION (un evento
     * de sede única siempre da una sola fila).
     */
    @Query("""
        SELECT r.branch.id, r.branch.name, COUNT(r), COALESCE(SUM(r.finalPrice), 0)
        FROM EventRegistration r
        WHERE r.event.id = :eventId
          AND r.status <> 'CANCELLED'
        GROUP BY r.branch.id, r.branch.name
    """)
    List<Object[]> branchReport(@Param("eventId") UUID eventId);

}