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
        name = "permissions",
        indexes = {

                @Index(
                        name = "idx_permission_code",
                        columnList = "code"
                ),

                @Index(
                        name = "idx_permission_status",
                        columnList = "status"
                )

        }
)
@Getter
@Setter
public class Permission extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================
    // IDENTIFICATION
    // =========================

    /**
     * Código interno del permiso.
     *
     * Ejemplo:
     *
     * USER_CREATE
     * USER_UPDATE
     * DOCUMENT_VIEW
     */
    @Column(
            nullable = false,
            unique = true
    )
    private String code;

    @Column(nullable = false)
    private String name;

    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false
    )
    private StatusType status;

    // =========================
    // HELPERS
    // =========================

    public boolean isActive(){
        return status == StatusType.ACTIVE;
    }

}