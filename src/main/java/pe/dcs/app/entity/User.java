package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name="users",
        indexes={

                @Index(
                        name="idx_user_org_branch",
                        columnList="organization_id,branch_id"
                ),

                @Index(
                        name="idx_user_dni",
                        columnList="dni"
                )

        }
)
@Getter
@Setter
public class User extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable=false)
    private String name;

    private String lastname;

    private String dni;

    private String sex;

    private String phone;

    private String address;

    private String dateBirth;

    private String maritalStatus;

    private String children;

    private String dateAdmission;

    /**
     * Organización donde pertenece la persona
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="organization_id"
    )
    private Organization organization;

    /**
     * Sede actual donde pertenece
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="branch_id"
    )
    private Branch branch;

    /**
     * Login del sistema
     * opcional
     */
    @OneToOne(
            mappedBy="user",
            cascade=CascadeType.ALL
    )
    private Credential credential;

    /**
     * Permisos del sistema
     */
    @OneToMany(
            mappedBy="user",
            cascade=CascadeType.ALL
    )
    private List<UserAccess> accesses =
            new ArrayList<>();

    /**
     * Membresía opcional
     */
    @OneToMany(
            mappedBy="user"
    )
    private List<Membership> memberships =
            new ArrayList<>();

}