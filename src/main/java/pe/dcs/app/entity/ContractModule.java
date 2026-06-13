package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.enums.StatusType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "contract_modules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contract_module",
                        columnNames = {"contract_id", "module_id"}
                )
        },
        indexes = {
                @Index(name = "idx_cm_contract", columnList = "contract_id"),
                @Index(name = "idx_cm_module", columnList = "module_id")
        }
)
@Getter
@Setter
public class ContractModule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "contract_id",
            nullable = false
    )
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "module_id",
            nullable = false
    )
    private Module module;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StatusType status;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "create_at")
    private LocalDateTime createdAt;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

}