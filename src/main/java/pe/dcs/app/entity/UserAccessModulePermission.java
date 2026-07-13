package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;

import java.util.UUID;

@Entity
@Table(
        name = "user_access_module_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_access_module_permission",
                        columnNames = {
                                "user_access_module_id",
                                "permission_id"
                        }
                )
        }
)
@Getter
@Setter
public class UserAccessModulePermission extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_access_module_id",
            nullable = false
    )
    private UserAccessModule userAccessModule;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permission_id",
            nullable = false
    )
    private Permission permission;

}