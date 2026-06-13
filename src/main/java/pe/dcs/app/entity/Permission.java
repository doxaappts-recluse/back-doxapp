package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.Auditable;

import java.util.UUID;

@Entity
@Table(
        name = "permissions",
        indexes = {
                @Index(name = "idx_permission_code", columnList = "code")
        }
)
@Getter
@Setter
public class Permission extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(
            unique = true,
            nullable = false
    )
    private String code;

    @Column(nullable = false)
    private String name;
}