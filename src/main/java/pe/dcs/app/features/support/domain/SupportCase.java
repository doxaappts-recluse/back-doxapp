package pe.dcs.app.features.support.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.TenantScoped;

import java.time.Instant;
import java.util.UUID;

/** M22 · caso de soporte entre una organización y el equipo de plataforma. */
@Getter
@Setter
@Entity
@Table(name = "support_case")
public class SupportCase extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_number", nullable = false, updatable = false)
    private long caseNumber;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Column(name = "opened_by_name", nullable = false, length = 170)
    private String openedByName;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private SupportCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private SupportPriority priority = SupportPriority.NORMAL;

    @Column(name = "subject", nullable = false, length = 150)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "sla_alerted_at")
    private Instant slaAlertedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "satisfaction")
    private Short satisfaction;

    @Column(name = "satisfaction_comment", length = 500)
    private String satisfactionComment;

    /**
     * M22 · enlace de solo lectura a la {@code approval_request} abierta automáticamente para categorías
     * CONTRACT_CHANGE/NEW_BRANCH (sin FK dura, mismo patrón que otros enlaces opcionales entre módulos).
     * Al resolver el caso ({@link pe.dcs.app.features.support.service.SupportService#changeStatus}), si está
     * presente, se aprueba con {@code ApprovalEngine.decideInternal} — para NEW_BRANCH_REQUEST eso crea la sede
     * de verdad (M04); para CONTRACT_REQUEST el cambio ya lo hizo SYSTEM_ADMIN a mano en M03 y esto solo cierra
     * el ciclo. Null en cualquier otra categoría.
     */
    @Column(name = "approval_request_id")
    private UUID approvalRequestId;
}
