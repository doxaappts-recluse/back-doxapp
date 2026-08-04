package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.followup.FollowUpContactMethod;
import pe.dcs.app.util.enums.followup.FollowUpContactResult;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de un contacto de seguimiento pastoral (llamada, visita,
 * WhatsApp, etc.) sobre una Person cualquiera — miembro, visitante
 * o cualquier otra. Genérico a propósito: no es exclusivo del
 * módulo Visitantes, también sirve para "seguimiento de miembros
 * inactivos" y similares (ver Person.assignedLeader).
 */
@Entity
@Table(
        name = "follow_up_contacts",
        indexes = {
                @Index(name = "idx_followup_contact_person", columnList = "person_id"),
                @Index(name = "idx_followup_contact_date", columnList = "contact_date"),
                @Index(name = "idx_followup_contact_branch", columnList = "branch_id")
        }
)
@Getter
@Setter
public class FollowUpContact extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "contact_date", nullable = false)
    private LocalDate contactDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", nullable = false)
    private FollowUpContactMethod contactMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private FollowUpContactResult result;

    @Column(length = 1000)
    private String notes;

    /**
     * Sede desde la que se registró el contacto — mismo propósito
     * que Baptism.branch/Membership.branch (scoping).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
