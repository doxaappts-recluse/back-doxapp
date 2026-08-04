package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.visitor.VisitorConsolidationStage;
import pe.dcs.app.util.enums.visitor.VisitorHowArrived;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos específicos de un visitante: cómo llegó, en qué etapa de
 * consolidación está y, si se integra, la fecha en que se convirtió
 * en miembro. 1:1 con una Person (toda Person con un Visitor
 * asociado es, por definición, alguien que llegó como visitante en
 * algún momento — eso no cambia aunque luego se convierta en
 * miembro, queda como dato histórico).
 *
 * El seguimiento en sí (historial de contactos, líder asignado,
 * peticiones de oración) NO vive acá — es genérico a cualquier
 * Person y vive en Person.assignedLeader/FollowUpContact/
 * PrayerRequest (ver features.pastoral_followup). Este entity solo
 * agrega lo que es específico de "ser visitante".
 */
@Entity
@Table(
        name = "visitors",
        indexes = {
                @Index(name = "idx_visitor_person", columnList = "person_id"),
                @Index(name = "idx_visitor_stage", columnList = "consolidation_stage"),
                @Index(name = "idx_visitor_branch", columnList = "branch_id")
        }
)
@Getter
@Setter
public class Visitor extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Column(name = "first_visit_date", nullable = false)
    private LocalDate firstVisitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "how_arrived", nullable = false)
    private VisitorHowArrived howArrived;

    /**
     * Solo relevante si howArrived=INVITED_BY_MEMBER. Puede ser
     * cualquier Person (no necesariamente un miembro activo, igual
     * criterio de Marriage.spouse1Person: se guarda el vínculo si se
     * encontró, sin más validación).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private Person invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "consolidation_stage", nullable = false)
    private VisitorConsolidationStage consolidationStage;

    /**
     * Se setea al convertir a miembro (ver
     * VisitorServiceImpl.convertToMember) — no se borra el registro
     * de Visitor al convertirse, queda como historial de cómo llegó.
     */
    @Column(name = "converted_at")
    private LocalDate convertedAt;

    @Column(length = 1000)
    private String notes;

    /**
     * Sede en la que se registró como visitante por primera vez.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
