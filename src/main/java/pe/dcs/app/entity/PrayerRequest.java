package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.followup.PrayerRequestStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Petición de oración de una Person cualquiera — miembro, visitante
 * o cualquier otra. Genérico igual que FollowUpContact (ver
 * Person.assignedLeader): no es exclusivo de Visitantes.
 */
@Entity
@Table(
        name = "prayer_requests",
        indexes = {
                @Index(name = "idx_prayer_request_person", columnList = "person_id"),
                @Index(name = "idx_prayer_request_status", columnList = "status"),
                @Index(name = "idx_prayer_request_branch", columnList = "branch_id")
        }
)
@Getter
@Setter
public class PrayerRequest extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrayerRequestStatus status;

    /**
     * Si es confidencial, no debería listarse en vistas grupales
     * (ver PrayerRequestServiceImpl) — igual criterio de "sensible"
     * que Membership/Marriage con visibilidad, pero acá el filtro es
     * a nivel de campo, no de VisibilityGrant.
     */
    @Column(nullable = false)
    private boolean confidential;

    @Column(name = "answered_notes", length = 1000)
    private String answeredNotes;

    /**
     * Sede desde la que se registró la petición.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
