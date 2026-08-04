package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Participante de un grupo pequeño / célula. Puede estar vinculado a
 * una Person existente (member o no-member, ambos pueden tener
 * registro Person) o, si la persona no tiene ningún registro en el
 * sistema, quedar solo con guestName/guestPhone — los grupos
 * pequeños explícitamente NO son exclusivos de miembros.
 */
@Entity
@Table(
        name = "small_group_members",
        indexes = {
                @Index(name = "idx_small_group_member_group", columnList = "group_id"),
                @Index(name = "idx_small_group_member_person", columnList = "person_id")
        }
)
@Getter
@Setter
public class SmallGroupMember extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private SmallGroup group;

    /**
     * Vínculo opcional a Person — nulo cuando el participante no
     * tiene ningún registro (ni miembro ni visitante registrado) y
     * solo se guarda su nombre/teléfono manualmente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;
}
