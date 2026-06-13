package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "event_attendances",
        indexes = {
                @Index(
                        name = "idx_attendance_registration",
                        columnList = "registration_id"
                ),
                @Index(
                        name = "idx_attendance_date",
                        columnList = "attended_at"
                )
        }
)
@Getter
@Setter
public class EventAttendance extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "registration_id",
            nullable = false,
            unique = true
    )
    private EventRegistration registration;

    @Column(name = "attended_at")
    private LocalDateTime attendedAt;

    private String observations;
}