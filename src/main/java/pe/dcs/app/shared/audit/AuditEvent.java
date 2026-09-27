package pe.dcs.app.shared.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** M22 · evento de auditoría append-only (la BD rechaza UPDATE/DELETE por trigger). */
@Getter
@Setter
@Immutable
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @Column(name = "actor_type", nullable = false, length = 10, updatable = false)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_role", length = 30, updatable = false)
    private String actorRole;

    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "module_code", nullable = false, length = 40, updatable = false)
    private String moduleCode;

    @Column(name = "action", nullable = false, length = 40, updatable = false)
    private String action;

    @Column(name = "entity_type", length = 60, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> diff;

    @Column(name = "ip", length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "assisted_grant_id", updatable = false)
    private UUID assistedGrantId;
}
