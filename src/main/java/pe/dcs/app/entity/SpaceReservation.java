package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.space_reservation.ReservationSourceType;
import pe.dcs.app.util.enums.space_reservation.ReservationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reserva de un {@link ReservableSpace} en un rango de fecha/hora.
 * Se crea directamente CONFIRMED si no cruza con otra reserva
 * CONFIRMED del mismo espacio (ver SpaceReservationServiceImpl.
 * assertNoOverlap) — sin flujo de aprobación en v1.
 *
 * {@code sourceType}/{@code sourceId} vinculan opcionalmente la
 * reserva a un Evento, Grupo Pequeño o Dictado de Academia Bíblica
 * existente (pedido explícito del usuario: "vincúlala mejor"), pero
 * SIN llave foránea real — es solo un UUID guardado a propósito, para
 * no acoplar este paquete con esos otros módulos. Por eso: (a) si el
 * registro origen se borra o cambia de nombre después, la reserva no
 * se entera sola (purpose queda como snapshot al crear), y (b)
 * cancelar el Evento/Grupo/Dictado NO cancela automaticamente esta
 * reserva en v1 — queda como mejora futura si hace falta.
 */
@Entity
@Table(
        name = "space_reservations",
        indexes = {
                @Index(name = "idx_space_reservation_space", columnList = "space_id"),
                @Index(name = "idx_space_reservation_status", columnList = "status")
        }
)
@Getter
@Setter
public class SpaceReservation extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private ReservableSpace space;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationSourceType sourceType;

    /** No nulo cuando sourceType != OTHER. Sin FK real — ver doc de la clase. */
    @Column(name = "source_id")
    private UUID sourceId;

    /** Motivo/actividad — texto libre si sourceType=OTHER, o snapshot del nombre del origen vinculado. */
    @Column(nullable = false, length = 500)
    private String purpose;

    /** Responsable vinculado a una Person, opcional — igual criterio que SmallGroup.leaderPerson/leaderName. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_person_id")
    private Person requesterPerson;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(length = 1000)
    private String notes;
}
