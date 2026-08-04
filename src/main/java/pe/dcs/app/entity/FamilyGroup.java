package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Grupo familiar / hogar: agrupa Person que conviven o forman parte de
 * la misma familia, cada una con un rol (ver {@link FamilyMember} /
 * {@link pe.dcs.app.util.enums.FamilyRole}). Es un realce gratuito de
 * Gestión de Miembros (no es un módulo comercial propio — ver
 * import.sql), a diferencia de módulos como Matrimonios que sí tienen
 * precio en el documento comercial.
 *
 * Se crea manualmente (ver FamilyGroupServiceImpl.create, requiere un
 * "jefe de hogar" inicial) o automáticamente al registrar un
 * Matrimonio con al menos un cónyuge vinculado a Person (ver
 * FamilyGroupServiceImpl.syncFromMarriage) — en ese caso, hijos u
 * otros parientes se agregan siempre a mano.
 */
@Entity
@Table(
        name = "family_groups",
        indexes = {
                @Index(name = "idx_family_group_branch", columnList = "branch_id"),
                @Index(name = "idx_family_group_status", columnList = "status")
        }
)
@Getter
@Setter
public class FamilyGroup extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String observations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @OneToMany(
            mappedBy = "familyGroup",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<FamilyMember> members = new ArrayList<>();
}
