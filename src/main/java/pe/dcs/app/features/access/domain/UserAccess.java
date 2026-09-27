package pe.dcs.app.features.access.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.TenantScoped;
import pe.dcs.app.util.enums.RoleType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * M05 · Acceso de una persona a una organización con un rol (y sede, salvo ORG_ADMIN que va sin sede).
 * Una persona puede tener varios accesos → contextos (spec 00 §5).
 */
@Getter
@Setter
@Entity
@Table(name = "user_access")
public class UserAccess extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private RoleType role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccessStatus status = AccessStatus.INVITED;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    /** Vigente hoy: ACTIVE y dentro de [validFrom, validTo] (fecha calendario de la organización, D9). */
    public boolean isEffective(LocalDate today) {
        return status == AccessStatus.ACTIVE
                && !today.isBefore(validFrom)
                && (validTo == null || !today.isAfter(validTo));
    }
}
