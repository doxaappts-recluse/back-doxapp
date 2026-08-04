package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Culto recurrente de una sede (ej. "Culto Dominical", "Culto de
 * Oración") — catálogo simple, día/hora en texto libre (mismo
 * criterio que SmallGroup.meetingDay/meetingTime, no hay una
 * recurrencia estructurada). Forma parte del paquete comercial "CRM
 * Pastoral" (junto a Seguimiento Pastoral/Visitantes): reutiliza el
 * módulo PASTORAL_FOLLOWUP y su AccessGuard, sin módulo propio ni
 * línea de precio adicional (ver ChurchAttendanceAccessGuard/
 * ChurchServiceServiceImpl).
 */
@Entity
@Table(
        name = "church_services",
        indexes = {
                @Index(name = "idx_church_service_branch", columnList = "branch_id"),
                @Index(name = "idx_church_service_status", columnList = "status")
        }
)
@Getter
@Setter
public class ChurchService extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "day_of_week")
    private String dayOfWeek;

    @Column(name = "time_of_day")
    private String timeOfDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
