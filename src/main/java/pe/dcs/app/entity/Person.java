package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.MaritalStatusType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "persons",
        indexes = {

                @Index(
                        name = "idx_person_dni",
                        columnList = "dni"
                )

        }
)
@Getter
@Setter
public class Person extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String lastname;

    @Column(unique = true)
    private String dni;

    private String sex;

    private String phone;

    private String address;

    private LocalDate dateBirth;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "marital_status",
            nullable = false
    )
    private MaritalStatusType maritalStatus;

    private Integer children;

    private LocalDate dateAdmission;

    /**
     * Historial de sedes donde ha pertenecido
     * la persona.
     *
     * Solo un registro debe permanecer activo.
     */
    @OneToMany(
            mappedBy = "person",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<PersonBranch> branchHistory =
            new ArrayList<>();

    /**
     * Credenciales del sistema.
     *
     * Una persona puede existir sin usuario
     * para iniciar sesión.
     */
    @OneToOne(
            mappedBy = "person",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private Credential credential;

    /**
     * Roles y accesos al sistema.
     *
     * Una persona puede tener múltiples
     * accesos según el contexto.
     */
    @OneToMany(
            mappedBy = "person",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<UserAccess> accesses =
            new ArrayList<>();

    /**
     * Historial de membresías.
     */
    @OneToMany(
            mappedBy = "person",
            fetch = FetchType.LAZY
    )
    private List<Membership> memberships =
            new ArrayList<>();

}