package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Entity
@Table(
        name = "user_accesses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_person_org_branch_role",
                        columnNames = {
                                "person_id",
                                "organization_id",
                                "branch_id",
                                "role_id"
                        }
                )
        },
        indexes = {

                @Index(
                        name = "idx_access_person",
                        columnList = "person_id"
                ),

                @Index(
                        name = "idx_access_org",
                        columnList = "organization_id"
                ),

                @Index(
                        name = "idx_access_branch",
                        columnList = "branch_id"
                ),

                @Index(
                        name = "idx_access_role",
                        columnList = "role_id"
                ),

                @Index(
                        name = "idx_access_person_org_branch",
                        columnList = "person_id,organization_id,branch_id"
                )
        }
)
@Getter
@Setter
public class UserAccess extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /**
     * Persona que tiene acceso al sistema.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="person_id")
    private Person person;

    /**
     * Organización donde aplica el permiso.
     *
     * NULL:
     * - SYSTEM_ADMIN
     * - SYSTEM_SUPPORT
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /**
     * Sede donde aplica el permiso.
     *
     * NULL:
     * - SYSTEM_ADMIN
     * - SYSTEM_SUPPORT
     * - ORG_ADMIN
     *
     * ORG_ADMIN:
     * todas las sedes de la organización.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /**
     * Rol asignado dentro del contexto.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType active;

    // ==========================
    // HELPERS
    // ==========================

    public boolean isSystemAccess(){
        return role.isSystemRole();
    }

    public boolean isOrganizationAdmin(){
        return role.isOrganizationAdmin();
    }

    public boolean isBranchAdmin(){
        return role.isBranchAdmin();
    }

    public boolean isOrganizationUser(){
        return role.isOrganizationUser();
    }

    public boolean hasBranch(){
        return branch != null;
    }

    public boolean isBranchAccess(){
        return role.isBranchRole();
    }

}