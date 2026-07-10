package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.constant.RoleConstant;
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
     *
     * Ejemplo:
     * SYSTEM_ADMIN
     * SYSTEM_SUPPORT
     * ORG_ADMIN
     * ORG_BRANCH_ADMIN
     * ORG_USER
     */
    @Column(nullable = false, unique = true)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    // ==========================
    // HELPERS
    // ==========================

    public boolean isSystemRole(){
        return RoleConstant.SYSTEM_ADMIN.equals(value)
                ||
                RoleConstant.SYSTEM_SUPPORT.equals(value);
    }

    public boolean isOrganizationAdmin(){
        return RoleConstant.ORG_ADMIN.equals(value);
    }

    public boolean isBranchAdmin(){
        return RoleConstant.ORG_BRANCH_ADMIN.equals(value);
    }

    public boolean isOrganizationUser(){
        return RoleConstant.ORG_USER.equals(value);
    }

    public boolean isBranchRole(){
        return isBranchAdmin()
                ||
                isOrganizationUser();
    }

}