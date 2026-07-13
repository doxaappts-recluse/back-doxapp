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
        name = "user_access_modules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_access_module",
                        columnNames = {
                                "user_access_id",
                                "module_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_uam_access",
                        columnList = "user_access_id"
                ),
                @Index(
                        name = "idx_uam_module",
                        columnList = "module_id"
                )
        }
)
@Getter
@Setter
public class UserAccessModule extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /**
     * Acceso de la persona dentro de un contexto:
     * - Organización
     * - Sede
     * - Rol
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_access_id",
            nullable = false
    )
    private UserAccess userAccess;

    /**
     * Módulo habilitado para este acceso.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "module_id",
            nullable = false
    )
    private Module module;

    /**
     * Estado del permiso.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    /**
     * Permite activar/desactivar temporalmente
     * el acceso al módulo.
     */
    @Column(nullable = false)
    private Boolean enabled = true;

}