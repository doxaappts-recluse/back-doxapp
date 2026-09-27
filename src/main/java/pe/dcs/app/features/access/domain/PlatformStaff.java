package pe.dcs.app.features.access.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.Auditable;
import pe.dcs.app.shared.vo.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** M05 · Personal de plataforma (D2). NO es Person y no pertenece a ninguna organización. */
@Getter
@Setter
@Entity
@Table(name = "platform_staff")
public class PlatformStaff extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 10)
    private DocumentType docType;

    @Column(name = "doc_number", nullable = false, length = 12)
    private String docNumber;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "position", length = 80)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_role", nullable = false, length = 20)
    private StaffRole staffRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessStatus status = AccessStatus.INVITED;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
