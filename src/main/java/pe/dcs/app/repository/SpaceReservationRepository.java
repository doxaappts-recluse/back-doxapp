package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.SpaceReservation;
import pe.dcs.app.util.enums.space_reservation.ReservationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface SpaceReservationRepository extends JpaRepository<SpaceReservation, UUID>, JpaSpecificationExecutor<SpaceReservation> {

    long countBySpaceId(UUID spaceId);

    /**
     * Solapamiento clásico de rangos: existe otra reserva CONFIRMED
     * del mismo espacio cuyo [startDateTime, endDateTime) cruza con
     * el rango solicitado. excludeId se usa al editar una reserva
     * existente, para no chocar contra sí misma.
     */
    @Query("""
        SELECT COUNT(r) > 0
        FROM SpaceReservation r
        WHERE r.space.id = :spaceId
          AND r.status = :status
          AND (:excludeId IS NULL OR r.id <> :excludeId)
          AND r.startDateTime < :endDateTime
          AND r.endDateTime > :startDateTime
    """)
    boolean existsOverlap(
            @Param("spaceId") UUID spaceId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("status") ReservationStatus status,
            @Param("excludeId") UUID excludeId
    );
}
