package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Entity
@Table(
        name = "roles",
        indexes = {
                @Index(
                        name = "idx_role_value",
                        columnList = "value"
                )
        }
)
@Getter
@Setter
public class Role extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String name;

    /**
     * Código interno del rol.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleType value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    // ==========================
    // HELPERS
    // ==========================

    public boolean isSystemRole() {
        return value == RoleType.SYSTEM_ADMIN
                || value == RoleType.SYSTEM_SUPPORT;
    }

    public boolean isOrganizationAdmin() {
        return value == RoleType.ORG_ADMIN;
    }

    public boolean isBranchAdmin() {
        return value == RoleType.ORG_BRANCH_ADMIN;
    }

    public boolean isOrganizationUser() {
        return value == RoleType.ORG_USER;
    }

    public boolean isBranchRole() {
        return isBranchAdmin()
                || isOrganizationUser();
    }
}