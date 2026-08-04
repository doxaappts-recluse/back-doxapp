package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grupo pequeño / célula de la iglesia. A diferencia de Ministerios
 * (que son roles de servicio dentro de la organización), un grupo
 * pequeño es una comunidad de reunión regular — el líder puede ser
 * un miembro (leaderPerson) o no (leaderName libre), y sus
 * participantes tampoco son exclusivos de miembros (ver
 * {@link SmallGroupMember}).
 *
 * startDate/endDate representan la "temporada" del grupo (endDate
 * null = temporada en curso). Mientras el grupo tenga un líder
 * vinculado a una Person con sede activa, esa temporada se refleja
 * automáticamente como un {@link MinistryAssignment} (servicio
 * ministerial) — ver SmallGroupServiceImpl.syncLeaderMinistryService.
 * ministryAssignment guarda el vínculo 1:1 con ese registro
 * generado, igual patrón que Marriage.financialMovement.
 */
@Entity
@Table(
        name = "small_groups",
        indexes = {
                @Index(name = "idx_small_group_branch", columnList = "branch_id"),
                @Index(name = "idx_small_group_status", columnList = "status")
        }
)
@Getter
@Setter
public class SmallGroup extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /**
     * Líder vinculado a una Person, opcional — si el líder no es
     * miembro (ni tiene registro alguno como Person), se usa
     * leaderName en su lugar. Igual criterio que los cónyuges de
     * Marriage: no se crea una Person solo para poder liderar un
     * grupo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_person_id")
    private Person leaderPerson;

    @Column(name = "leader_name")
    private String leaderName;

    @Column(name = "meeting_day")
    private String meetingDay;

    @Column(name = "meeting_time")
    private String meetingTime;

    @Column(name = "location")
    private String location;

    /**
     * Categoría/tag libre (p.ej. "Jóvenes", "Matrimonios", "Barrio
     * Norte") — no hay catálogo cerrado, cada iglesia organiza sus
     * grupos distinto.
     */
    @Column(name = "category")
    private String category;

    /**
     * Temporada del grupo. endDate null = temporada en curso, sin
     * fecha de cierre definida todavía.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Tema a tratar durante la temporada — opcional, texto libre
     * (p.ej. "Estudio del libro de Filipenses").
     */
    @Column(name = "topic", length = 500)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /**
     * Registro de servicio ministerial generado automáticamente
     * para el líder actual durante esta temporada — null si no hay
     * líder vinculado a una Person con sede activa (ver
     * SmallGroupServiceImpl).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_assignment_id")
    private MinistryAssignment ministryAssignment;

    @OneToMany(
            mappedBy = "group",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<SmallGroupMember> members = new ArrayList<>();
}
