package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.hr.HrApprovalStatus;
import pe.dcs.app.util.enums.hr.HrLeaveType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Solicitud de vacaciones/permiso de un {@link StaffMember}.
 * Delegable a org user con permiso CREATE/EDIT (ver HrAccessGuard) —
 * a diferencia de la ficha de empleado, tanto registrar la solicitud
 * como aprobarla/rechazarla es delegable a la sede.
 *
 * Editable (fechas/tipo/motivo) solo mientras siga PENDING — una vez
 * aprobada o rechazada queda como registro histórico, igual criterio
 * que FinancialMovement una vez APPROVED/REJECTED.
 */
@Entity
@Table(
        name = "hr_leave_requests",
        indexes = {
                @Index(name = "idx_leave_request_staff", columnList = "staff_id"),
                @Index(name = "idx_leave_request_status", columnList = "status"),
                @Index(name = "idx_leave_request_dates", columnList = "start_date, end_date")
        }
)
@Getter
@Setter
public class LeaveRequest extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private StaffMember staff;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HrLeaveType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HrApprovalStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private Person approvedByUser;

    @Column(name = "approved_at")
    private Instant approvedAt;

    /** Motivo del rechazo, o notas adicionales de la aprobación (mismo uso dual que FinancialMovement.observations). */
    @Column(length = 1000)
    private String observations;
}
