package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.FamilyRole;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Vínculo de una Person a un {@link FamilyGroup}, con su rol dentro de
 * la familia. A diferencia de SmallGroupMember, acá SIEMPRE hay una
 * Person vinculada (no hay "invitado" de solo nombre) — Grupo
 * Familiar vive bajo Personas y opera sobre gente que ya existe en el
 * sistema. Una Person pertenece a UN SOLO grupo familiar a la vez
 * (constraint unique en person_id) — mismo criterio que un hogar real.
 */
@Entity
@Table(
        name = "family_members",
        indexes = {
                @Index(name = "idx_family_member_group", columnList = "family_group_id"),
                @Index(name = "idx_family_member_person", columnList = "person_id", unique = true)
        }
)
@Getter
@Setter
public class FamilyMember extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_group_id", nullable = false)
    private FamilyGroup familyGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FamilyRole role;

    @Column(name = "join_date")
    private LocalDate joinDate;
}
