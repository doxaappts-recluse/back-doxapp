package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.UUID;

@Entity
@Table(
        name = "user_module_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_module_permission",
                        columnNames = {
                                "user_module_id",
                                "permission_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_ump_user_module",
                        columnList = "user_module_id"
                ),
                @Index(
                        name = "idx_ump_permission",
                        columnList = "permission_id"
                )
        }
)
@Getter
@Setter
public class UserModulePermission extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_module_id",
            nullable = false
    )
    private UserModule userModule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permission_id",
            nullable = false
    )
    private Permission permission;

}