package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name="visibility_grants",
        indexes = {
                @Index(name="idx_visibility_grant_person", columnList="person_id"),
                @Index(name="idx_visibility_grant_source", columnList="source_branch_id"),
                @Index(name="idx_visibility_grant_target", columnList="target_branch_id"),
                @Index(name="idx_visibility_grant_module", columnList="module_id"),
                @Index(name="idx_visibility_grant_active", columnList="active")
        }
)
@Getter
@Setter
public class VisibilityGrant extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Persona cuyos datos se pueden consultar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="person_id", nullable=false)
    private Person person;

    /** Sede dueña del dato. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="source_branch_id", nullable=false)
    private Branch sourceBranch;

    /** Sede autorizada a consultar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="target_branch_id", nullable=false)
    private Branch targetBranch;

    /** Módulo permitido. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="module_id", nullable=false)
    private Module module;

    /** Fecha máxima visible.
     * Ejemplo:
     * Juan estuvo en Norte hasta: 2026-06-01
     * Sur podrá ver hasta esa fecha */
    private LocalDate visibleUntil;

    @Column(nullable=false)
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="approved_by")
    private Person approvedBy;

    private Instant approvedAt;

}