package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de asistencia de una Person a un {@link ChurchService} en
 * una fecha puntual — a diferencia de {@link EventAttendance} (ligado
 * a una inscripción de Evento puntual), acá no hay "inscripción"
 * previa: simplemente se marca presente a quien asistió, buscada por
 * DNI (igual patrón que Matrimonios/Grupos Pequeños). SIEMPRE opera
 * sobre una Person que ya existe — no admite invitados de solo
 * nombre en esta primera versión.
 */
@Entity
@Table(
        name = "church_service_attendances",
        indexes = {
                @Index(name = "idx_church_attendance_service_date", columnList = "church_service_id,attendance_date"),
                @Index(name = "idx_church_attendance_person", columnList = "person_id")
        }
)
@Getter
@Setter
public class ChurchServiceAttendance extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "church_service_id", nullable = false)
    private ChurchService churchService;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(length = 500)
    private String observations;
}
