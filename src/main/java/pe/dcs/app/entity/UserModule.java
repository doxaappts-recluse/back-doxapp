package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

@Entity
@Table(
        name = "user_modules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_module",
                        columnNames = {
                                "user_id",
                                "module_id"
                        }
                )
        },
        indexes = {
                @Index(name = "idx_um_user", columnList = "user_id"),
                @Index(name = "idx_um_module", columnList = "module_id")
        }
)
@Getter
@Setter
public class UserModule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "module_id",
            nullable = false
    )
    private Module module;

    @Column(name = "enabled")
    private Boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

}