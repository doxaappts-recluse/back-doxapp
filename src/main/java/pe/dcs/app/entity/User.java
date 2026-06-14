package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_user_name", columnList = "name"),
                @Index(name = "idx_user_lastname", columnList = "lastname")
        }
)
@Getter
@Setter
public class User extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "lastname")
    private String lastname;

    private String dni;

    private String sex;

    private String phone;

    private String address;

    @Column(name = "date_birth")
    private String dateBirth;

    @Column(name = "marital_status")
    private String maritalStatus;

    private String children;

    @Column(name = "date_admission")
    private String dateAdmission;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Credential credential;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @OneToMany(mappedBy = "user")
    private List<Membership> memberships;

    @OneToMany(mappedBy = "user")
    private List<MemberMinistryAssignment> memberMinistryAssignments;

}