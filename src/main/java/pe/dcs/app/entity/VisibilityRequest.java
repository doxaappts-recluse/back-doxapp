package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.rules.VisibilityStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name="visibility_requests",
        indexes = {
                @Index(name="idx_visibility_person", columnList="person_id"),
                @Index(name="idx_visibility_request_branch", columnList="request_branch_id"),
                @Index(name="idx_visibility_source_branch", columnList="source_branch_id"),
                @Index(name="idx_visibility_module", columnList="module_id"),
                @Index(name="idx_visibility_status", columnList="status"),
                @Index(name="idx_visibility_lookup", columnList="person_id,source_branch_id,module_id,status")
        }
)
@Getter
@Setter
public class VisibilityRequest extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="person_id",nullable=false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="request_branch_id",nullable=false)
    private Branch requestBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="source_branch_id",nullable=false)
    private Branch sourceBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="module_id",nullable=false)
    private Module module;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="requested_by",nullable=false)
    private Person requestedBy;

    @Column(length=500)
    private String reason;

    private LocalDate requestedFrom;

    private LocalDate requestedUntil;

    private LocalDate approvedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private VisibilityStatus status;

    private Instant approvedAt;

    private Instant rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="approved_by")
    private Person approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="rejected_by")
    private Person rejectedBy;

}